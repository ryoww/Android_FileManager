package com.ryo.androidfilemanager.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.SourceType
import com.ryo.androidfilemanager.core.domain.ViewerType
import com.ryo.androidfilemanager.core.domain.detectViewerType
import com.ryo.androidfilemanager.data.smb.RemoteMediaDataSource
import com.ryo.androidfilemanager.data.smb.SmbConnectionPool
import com.ryo.androidfilemanager.data.smb.SmbProxyFileDescriptors
import com.ryo.androidfilemanager.data.source.SmbFileSource
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class SmbThumbnailRepository(
    context: Context,
    private val sourceProvider: () -> SmbFileSource?,
) : ThumbnailRepository {
    private val appContext = context.applicationContext
    private val localRepository = FileThumbnailRepository(appContext)
    private val smbThumbnailDir = File(appContext.cacheDir, "smb_thumbnails")
    private val memoryCache = LruCache<String, ThumbnailResult.CachedFile>(MEMORY_CACHE_SIZE)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val thumbnailVersion = MutableStateFlow(0L)
    private val requestCoordinator = SmbThumbnailRequestCoordinator(
        elapsedRealtime = { SystemClock.elapsedRealtime() },
        failedKeyTtlMs = FAILED_KEY_TTL_MS,
    )
    private val updateSignal = Channel<Unit>(Channel.CONFLATED)

    init {
        repositoryScope.launch {
            processThumbnailRequests(ViewerType.Pdf)
        }
        repeat(IMAGE_WORKER_COUNT) {
            repositoryScope.launch {
                processThumbnailRequests(ViewerType.Image)
            }
        }
        repeat(VIDEO_WORKER_COUNT) {
            repositoryScope.launch {
                processThumbnailRequests(ViewerType.Video)
            }
        }
        repositoryScope.launch {
            processThumbnailUpdateSignals()
        }
    }

    override suspend fun getThumbnail(file: FileItem): ThumbnailResult = withContext(Dispatchers.IO) {
        if (file.sourceType != SourceType.SMB) {
            return@withContext localRepository.getThumbnail(file)
        }

        if (file.isDirectory) {
            return@withContext ThumbnailResult.Icon(IconResolver.resolve(file))
        }

        val viewerType = detectViewerType(file.name, file.mimeType)
        val key = thumbnailKey(file)
        if (!viewerType.supportsSmbThumbnail()) {
            return@withContext ThumbnailResult.Icon(IconResolver.resolve(file))
        }

        val cacheFile = cacheFileFor(file, viewerType.smbCacheExtension())
        val memoryResult = memoryCache.get(key)
        when {
            memoryResult != null && cacheFile.exists() -> memoryResult
            cacheFile.exists() -> cachedFileResult(key, cacheFile)
            else -> ThumbnailResult.Icon(IconResolver.resolve(file))
        }
    }

    override suspend fun generateThumbnail(file: FileItem): ThumbnailResult = withContext(Dispatchers.IO) {
        if (file.sourceType != SourceType.SMB) {
            return@withContext localRepository.generateThumbnail(file)
        }

        val source = sourceProvider()
            ?: return@withContext ThumbnailResult.Icon(IconResolver.resolve(file))
        val viewerType = detectViewerType(file.name, file.mimeType)
        if (!shouldGenerateSmbThumbnail(file, viewerType)) {
            return@withContext ThumbnailResult.Icon(IconResolver.resolve(file))
        }

        // PDF・動画はランダムアクセスで必要な部分だけ読んでレンダリングする
        // (巨大ファイルでも全量ダウンロードしない)
        if (viewerType == ViewerType.Pdf) {
            return@withContext generateSmbPdfThumbnail(source, file)
        }
        if (viewerType == ViewerType.Video) {
            return@withContext generateSmbVideoThumbnail(source, file)
        }

        // JPEG は EXIF 埋め込みサムネイルを先頭バイトだけで抽出できることが多く、
        // 全量ダウンロードを回避できる。失敗時のみ全量ダウンロードへフォールバック。
        smbDownloadSemaphore.withPermit {
            tryExifThumbnail(source, file)
        }?.let { return@withContext it }

        val previewFile = smbDownloadSemaphore.withPermit {
            runCatching { source.cachePreviewFile(file) }.getOrNull()
        } ?: return@withContext ThumbnailResult.Icon(IconResolver.resolve(file))

        return@withContext runCatching {
            val localFile = file.copy(
                path = previewFile.path,
                uri = Uri.fromFile(previewFile).toString(),
                sourceType = SourceType.LOCAL,
            )
            val result = localRepository.generateThumbnail(localFile)
            if (result !is ThumbnailResult.CachedFile) {
                return@runCatching result
            }

            val smbCacheFile = cacheFileFor(file, viewerType.smbCacheExtension())
            smbThumbnailDir.mkdirs()
            val generatedFile = Uri.parse(result.uri).path?.let(::File)
                ?: return@runCatching ThumbnailResult.Icon(IconResolver.resolve(file))
            generatedFile.copyTo(smbCacheFile, overwrite = true)
            cachedFileResult(thumbnailKey(file), smbCacheFile)
        }.getOrElse {
            ThumbnailResult.Icon(IconResolver.resolve(file))
        }
    }

    private suspend fun generateSmbPdfThumbnail(
        source: SmbFileSource,
        file: FileItem,
    ): ThumbnailResult {
        val remote = runCatching { source.openPooledRandomAccess(file) }.getOrNull()
            ?: return ThumbnailResult.Icon(IconResolver.resolve(file))

        val descriptor = runCatching {
            SmbProxyFileDescriptors.open(
                context = appContext,
                remote = remote,
                readBudgetBytes = PDF_PROXY_READ_BUDGET_BYTES,
                logLabel = file.name,
            )
        }.getOrElse {
            remote.close()
            return ThumbnailResult.Icon(IconResolver.resolve(file))
        }

        val cacheFile = cacheFileFor(file, "jpg")
        val rendered = runCatching {
            PdfThumbnailGenerator.renderFirstPage(
                descriptor = descriptor,
                output = cacheFile,
                maxSize = PDF_THUMBNAIL_MAX_SIZE,
                jpegQuality = PDF_THUMBNAIL_JPEG_QUALITY,
            )
        }.getOrDefault(false)

        return if (rendered) {
            cachedFileResult(thumbnailKey(file), cacheFile)
        } else {
            ThumbnailResult.Unavailable("PDF thumbnail could not be rendered over SMB.")
        }
    }

    private suspend fun generateSmbVideoThumbnail(
        source: SmbFileSource,
        file: FileItem,
    ): ThumbnailResult {
        val key = thumbnailKey(file)
        val cacheFile = cacheFileFor(file, "jpg")
        val sidecarStartedAt = SystemClock.elapsedRealtime()
        val sidecarHit = runCatching {
            source.copyFreshThumbnailSidecar(file, cacheFile)
        }.getOrDefault(false)
        if (sidecarHit) {
            Log.d(
                SmbConnectionPool.PERF_TAG,
                "video sidecar hit: ${file.name} ${cacheFile.length() / 1024}KB " +
                    "in ${SystemClock.elapsedRealtime() - sidecarStartedAt}ms",
            )
            return cachedFileResult(key, cacheFile)
        }

        val remote = runCatching { source.openPooledRandomAccess(file) }.getOrNull()
            ?: return ThumbnailResult.Icon(IconResolver.resolve(file))

        val readProfile = smbVideoReadProfile(file.name)
        val dataSource = RemoteMediaDataSource(
            remote = remote,
            readBudgetBytes = VIDEO_READ_BUDGET_BYTES,
            chunkSize = readProfile.chunkSize,
            maxCachedChunks = readProfile.maxCachedChunks,
        )
        if (!registerRunningVideoSource(key, dataSource)) {
            return ThumbnailResult.Unavailable(
                "Video thumbnail request is no longer visible.",
                retryable = true,
            )
        }
        // MediaMetadataRetriever がネイティブ側で固まるとワーカーが永久占有され、
        // 以降の動画サムネイルが全て止まる。一定時間で読み取りを打ち切る。
        // watchdog スレッドの書き込みをワーカースレッドが確実に読めるよう Atomic にする
        // (stale 読みで retryable と誤判定すると同じ動画を延々と再試行してしまう)
        val timedOut = AtomicBoolean(false)
        val watchdog = repositoryScope.launch {
            delay(readProfile.renderTimeoutMs)
            timedOut.set(true)
            Log.w(
                SmbConnectionPool.PERF_TAG,
                "video watchdog: ${file.name} exceeded ${readProfile.renderTimeoutMs}ms, cancelling",
            )
            dataSource.cancel()
        }
        val rendered = runCatching {
            VideoThumbnailGenerator.renderFrame(
                dataSource = dataSource,
                output = cacheFile,
                maxSize = VIDEO_THUMBNAIL_MAX_SIZE,
                jpegQuality = VIDEO_THUMBNAIL_JPEG_QUALITY,
                options = VideoThumbnailRenderPresets.SmbFast,
            )
        }.getOrDefault(false)
        watchdog.cancel()
        Log.d(
            SmbConnectionPool.PERF_TAG,
            "video source: ${file.name} fetched ${dataSource.fetchedBytes / 1024}KB",
        )
        val cancelledMidFlight = dataSource.isCancelled && !timedOut.get()
        unregisterRunningVideoSource(key)
        runCatching { dataSource.close() }

        return if (rendered) {
            val result = cachedFileResult(key, cacheFile)
            repositoryScope.launch {
                val stored = runCatching {
                    source.storeThumbnailSidecar(file, cacheFile)
                }.getOrDefault(false)
                Log.d(
                    SmbConnectionPool.PERF_TAG,
                    "video sidecar store: ${file.name} ${if (stored) "stored" else "skipped"}",
                )
            }
            result
        } else {
            ThumbnailResult.Unavailable(
                "Video thumbnail could not be rendered over SMB.",
                // 可視域外へのスクロールで中断されただけなら失敗として固定しない
                // (watchdog 打ち切りは高コストな再試行を防ぐため failed 扱いのまま)
                retryable = cancelledMidFlight,
            )
        }
    }

    private suspend fun tryExifThumbnail(
        source: SmbFileSource,
        file: FileItem,
    ): ThumbnailResult.CachedFile? {
        if (!file.isJpegImage()) {
            return null
        }

        return runCatching {
            val head = source.readHeadBytes(file, EXIF_HEAD_READ_BYTES)
            if (head.isEmpty()) {
                return@runCatching null
            }

            val exif = ExifInterface(ByteArrayInputStream(head))
            val thumbnail = exif.thumbnailBitmap
            if (thumbnail == null) {
                Log.d(SmbConnectionPool.PERF_TAG, "exif miss: ${file.name} (no embedded thumbnail)")
                return@runCatching null
            }
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
            val oriented = thumbnail.applyExifOrientation(orientation)

            val cacheFile = cacheFileFor(file, "jpg")
            smbThumbnailDir.mkdirs()
            FileOutputStream(cacheFile).use { output ->
                oriented.compress(Bitmap.CompressFormat.JPEG, EXIF_THUMBNAIL_JPEG_QUALITY, output)
            }
            Log.d(SmbConnectionPool.PERF_TAG, "exif hit: ${file.name}")
            cachedFileResult(thumbnailKey(file), cacheFile)
        }.getOrNull()
    }

    override fun requestThumbnail(file: FileItem) {
        if (file.sourceType != SourceType.SMB) {
            localRepository.requestThumbnail(file)
            return
        }

        repositoryScope.launch {
            enqueueThumbnail(file, ThumbnailRequestPriority.Background)
        }
    }

    override fun updateVisibleThumbnails(files: List<FileItem>) {
        val localFiles = files.filter { file -> file.sourceType != SourceType.SMB }
        if (localFiles.isNotEmpty()) {
            localRepository.updateVisibleThumbnails(localFiles)
        }

        val supportedSmbFiles = files.filter { file ->
            file.sourceType == SourceType.SMB &&
                !file.isDirectory &&
                shouldGenerateSmbThumbnail(file, detectViewerType(file.name, file.mimeType))
        }
        // 画面上の並び順をキューへ渡し、先頭にある動画から生成する。
        val nextVisibleKeys = supportedSmbFiles.map { file ->
            thumbnailKey(file)
        }
        val viewportGeneration = requestCoordinator.newViewportGeneration()

        repositoryScope.launch {
            val cancellations = requestCoordinator.applyViewportSnapshot(
                generation = viewportGeneration,
                nextVisibleKeys = nextVisibleKeys,
            ) ?: return@launch
            cancellations.forEach { cancel -> cancel() }
            supportedSmbFiles.forEach { file ->
                enqueueThumbnail(file, ThumbnailRequestPriority.Visible)
            }
        }
    }

    override fun observeThumbnailVersion(): StateFlow<Long> = thumbnailVersion.asStateFlow()

    override fun resetFailedThumbnails() {
        localRepository.resetFailedThumbnails()
        repositoryScope.launch {
            requestCoordinator.clearFailed()
        }
    }

    override suspend fun clearThumbnailCache() {
        localRepository.clearThumbnailCache()
        withContext(Dispatchers.IO) {
            smbThumbnailDir.deleteRecursively()
            smbThumbnailDir.mkdirs()
        }
        memoryCache.evictAll()
        requestCoordinator.clearFailed()
        notifyThumbnailUpdated()
    }

    override fun close() {
        localRepository.close()
        repositoryScope.cancel()
    }

    private suspend fun enqueueThumbnail(
        file: FileItem,
        requestedPriority: ThumbnailRequestPriority,
    ) {
        if (file.isDirectory) {
            return
        }

        val viewerType = detectViewerType(file.name, file.mimeType)
        if (!shouldGenerateSmbThumbnail(file, viewerType)) {
            return
        }

        val key = thumbnailKey(file)
        val cacheFile = cacheFileFor(file, viewerType.smbCacheExtension())
        if (cacheFile.exists()) {
            return
        }

        requestCoordinator.enqueue(
            file = file,
            key = key,
            requestedPriority = requestedPriority,
        )
    }

    private suspend fun registerRunningVideoSource(
        key: String,
        dataSource: RemoteMediaDataSource,
    ): Boolean {
        val registered = requestCoordinator.registerRunningCancellable(key) {
            dataSource.cancel()
        }
        if (!registered) {
            dataSource.cancel()
        }
        return registered
    }

    private suspend fun unregisterRunningVideoSource(key: String) {
        requestCoordinator.unregisterRunningCancellable(key)
    }

    private suspend fun processThumbnailRequests(
        viewerType: ViewerType,
    ) {
        while (true) {
            val request = requestCoordinator.receive(viewerType)
            if (!requestCoordinator.markRunning(request)) {
                continue
            }

            val startedAt = SystemClock.elapsedRealtime()
            Log.d(
                SmbConnectionPool.PERF_TAG,
                "dequeue: ${request.file.name} waited ${startedAt - request.enqueuedAt}ms " +
                    "priority=${request.priority} generation=${request.viewportGeneration}",
            )
            val result = try {
                generateThumbnail(request.file)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.w(
                    SmbConnectionPool.PERF_TAG,
                    "generate failed: ${request.file.name}",
                    throwable,
                )
                ThumbnailResult.Unavailable("SMB thumbnail generation failed.")
            }
            Log.d(
                SmbConnectionPool.PERF_TAG,
                "generate done: ${request.file.name} ${result::class.simpleName} " +
                    "in ${SystemClock.elapsedRealtime() - startedAt}ms",
            )

            val stillWanted = requestCoordinator.finish(
                request = request,
                succeeded = result is ThumbnailResult.CachedFile,
                retryable = result is ThumbnailResult.Unavailable && result.retryable,
            )
            if (result is ThumbnailResult.CachedFile) {
                memoryCache.put(result.key, result)
                if (stillWanted) {
                    notifyThumbnailUpdated()
                }
            } else if (stillWanted && result is ThumbnailResult.Unavailable && result.retryable) {
                // 実行中に一度可視域から外れて中断されたが、いままた可視のケース。
                // 次のスクロールを待つと再エンキュー契機がなく永遠に出ないため即再試行する
                enqueueThumbnail(request.file, ThumbnailRequestPriority.Visible)
            }
        }
    }

    private suspend fun processThumbnailUpdateSignals() {
        for (signal in updateSignal) {
            thumbnailVersion.update { version -> version + 1L }
            delay(THUMBNAIL_UPDATE_THROTTLE_MS)
        }
    }

    private fun shouldGenerateSmbThumbnail(
        file: FileItem,
        viewerType: ViewerType,
    ): Boolean = viewerType.supportsSmbThumbnail()

    private fun notifyThumbnailUpdated() {
        updateSignal.trySend(Unit)
    }

    private fun cacheFileFor(file: FileItem, extension: String): File {
        val key = thumbnailKey(file).sha256()
        return File(smbThumbnailDir, "$key.$extension")
    }

    private fun cachedFileResult(
        key: String,
        cacheFile: File,
    ): ThumbnailResult.CachedFile {
        val result = ThumbnailResult.CachedFile(
            key = key,
            uri = Uri.fromFile(cacheFile).toString(),
        )
        memoryCache.put(key, result)
        return result
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val MEMORY_CACHE_SIZE = 512
        const val THUMBNAIL_UPDATE_THROTTLE_MS = 160L
        const val IMAGE_WORKER_COUNT = 3
        const val EXIF_HEAD_READ_BYTES = 160 * 1024
        const val EXIF_THUMBNAIL_JPEG_QUALITY = 80
        const val PDF_PROXY_READ_BUDGET_BYTES = 32L * 1024L * 1024L
        const val PDF_THUMBNAIL_MAX_SIZE = 240
        const val PDF_THUMBNAIL_JPEG_QUALITY = 72
        const val VIDEO_READ_BUDGET_BYTES = 64L * 1024L * 1024L
        const val VIDEO_THUMBNAIL_MAX_SIZE = 240
        const val VIDEO_THUMBNAIL_JPEG_QUALITY = 72
        const val VIDEO_WORKER_COUNT = 2
        const val FAILED_KEY_TTL_MS = 5L * 60L * 1000L
        val smbDownloadSemaphore = Semaphore(3)
    }
}

internal data class SmbVideoReadProfile(
    val chunkSize: Long,
    val maxCachedChunks: Int,
    val renderTimeoutMs: Long,
)

internal fun smbVideoReadProfile(fileName: String): SmbVideoReadProfile {
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return if (extension == "webm" || extension == "mkv") {
        SmbVideoReadProfile(
            chunkSize = 2L * 1024L * 1024L,
            maxCachedChunks = 16,
            renderTimeoutMs = 6_000L,
        )
    } else {
        SmbVideoReadProfile(
            chunkSize = 512L * 1024L,
            maxCachedChunks = 48,
            renderTimeoutMs = 12_000L,
        )
    }
}

private fun FileItem.isJpegImage(): Boolean {
    if (mimeType == "image/jpeg") {
        return true
    }
    val lowerName = name.lowercase()
    return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
}

private fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun ViewerType.supportsSmbThumbnail(): Boolean =
    this == ViewerType.Pdf || this == ViewerType.Image || this == ViewerType.Video

private fun ViewerType.smbCacheExtension(): String = when (this) {
    ViewerType.Pdf -> "jpg"
    ViewerType.Image -> "jpg"
    ViewerType.Video -> "jpg"
    else -> "png"
}
