package com.ryo.androidfilemanager.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.SourceType
import com.ryo.androidfilemanager.data.model.ViewerType
import com.ryo.androidfilemanager.data.source.SmbFileSource
import com.ryo.androidfilemanager.data.source.detectViewerType
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
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
    private val pdfRequests = Channel<QueuedSmbThumbnailRequest>(Channel.UNLIMITED)
    private val imageRequests = Channel<QueuedSmbThumbnailRequest>(Channel.UNLIMITED)
    private val updateSignal = Channel<Unit>(Channel.CONFLATED)
    private val inFlightMutex = Mutex()
    private val inFlightKeys = mutableSetOf<String>()

    init {
        repositoryScope.launch {
            processThumbnailRequests(pdfRequests)
        }
        repeat(IMAGE_WORKER_COUNT) {
            repositoryScope.launch {
                processThumbnailRequests(imageRequests)
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
            val thumbnail = exif.thumbnailBitmap ?: return@runCatching null
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
            cachedFileResult(thumbnailKey(file), cacheFile)
        }.getOrNull()
    }

    override fun requestThumbnail(file: FileItem) {
        if (file.sourceType != SourceType.SMB) {
            localRepository.requestThumbnail(file)
            return
        }

        if (file.isDirectory) {
            return
        }

        val viewerType = detectViewerType(file.name, file.mimeType)
        if (!shouldGenerateSmbThumbnail(file, viewerType)) {
            return
        }

        repositoryScope.launch {
            val key = thumbnailKey(file)
            val cacheFile = cacheFileFor(file, viewerType.smbCacheExtension())
            if (cacheFile.exists()) {
                return@launch
            }

            val shouldQueue = inFlightMutex.withLock {
                if (key in inFlightKeys) {
                    false
                } else {
                    inFlightKeys += key
                    true
                }
            }

            if (shouldQueue) {
                viewerType.requestChannel().trySend(
                    QueuedSmbThumbnailRequest(
                        file = file,
                        key = key,
                    ),
                )
            }
        }
    }

    override fun observeThumbnailVersion(): StateFlow<Long> = thumbnailVersion.asStateFlow()

    override suspend fun clearThumbnailCache() {
        localRepository.clearThumbnailCache()
        withContext(Dispatchers.IO) {
            smbThumbnailDir.deleteRecursively()
            smbThumbnailDir.mkdirs()
        }
        memoryCache.evictAll()
        notifyThumbnailUpdated()
    }

    private suspend fun processThumbnailRequests(
        requests: Channel<QueuedSmbThumbnailRequest>,
    ) {
        for (request in requests) {
            try {
                val result = generateThumbnail(request.file)
                if (result is ThumbnailResult.CachedFile) {
                    memoryCache.put(result.key, result)
                    notifyThumbnailUpdated()
                }
            } finally {
                inFlightMutex.withLock {
                    inFlightKeys -= request.key
                }
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
    ): Boolean {
        if (!viewerType.supportsSmbThumbnail()) {
            return false
        }
        return viewerType != ViewerType.Pdf ||
            (file.size ?: Long.MAX_VALUE) <= SMB_PDF_AUTO_THUMBNAIL_MAX_BYTES
    }

    private fun notifyThumbnailUpdated() {
        updateSignal.trySend(Unit)
    }

    private fun ViewerType.requestChannel(): Channel<QueuedSmbThumbnailRequest> = when (this) {
        ViewerType.Pdf -> pdfRequests
        ViewerType.Image -> imageRequests
        else -> error("Unsupported SMB thumbnail type: $this")
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
        const val SMB_PDF_AUTO_THUMBNAIL_MAX_BYTES = 20L * 1024L * 1024L
        const val MEMORY_CACHE_SIZE = 512
        const val THUMBNAIL_UPDATE_THROTTLE_MS = 160L
        const val IMAGE_WORKER_COUNT = 3
        const val EXIF_HEAD_READ_BYTES = 160 * 1024
        const val EXIF_THUMBNAIL_JPEG_QUALITY = 80
        val smbDownloadSemaphore = Semaphore(3)
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

private data class QueuedSmbThumbnailRequest(
    val file: FileItem,
    val key: String,
)

private fun ViewerType.supportsSmbThumbnail(): Boolean =
    this == ViewerType.Pdf || this == ViewerType.Image

private fun ViewerType.smbCacheExtension(): String = when (this) {
    ViewerType.Pdf -> "jpg"
    ViewerType.Image -> "jpg"
    else -> "png"
}
