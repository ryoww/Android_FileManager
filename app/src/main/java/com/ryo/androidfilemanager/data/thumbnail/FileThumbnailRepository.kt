package com.ryo.androidfilemanager.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.SourceType
import com.ryo.androidfilemanager.data.model.ViewerType
import com.ryo.androidfilemanager.data.source.detectViewerType
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.min
import kotlin.math.roundToInt
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

class FileThumbnailRepository(
    context: Context,
) : ThumbnailRepository {
    private val appContext = context.applicationContext
    private val thumbnailDir = File(appContext.cacheDir, "thumbnails")
    private val memoryCache = LruCache<String, ThumbnailResult.CachedFile>(MEMORY_CACHE_SIZE)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val thumbnailVersion = MutableStateFlow(0L)
    private val pdfRequests = Channel<QueuedThumbnailRequest>(Channel.UNLIMITED)
    private val imageRequests = Channel<QueuedThumbnailRequest>(Channel.UNLIMITED)
    private val videoRequests = Channel<QueuedThumbnailRequest>(Channel.UNLIMITED)
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
            processThumbnailRequests(videoRequests)
        }
        repositoryScope.launch {
            processThumbnailUpdateSignals()
        }
    }

    override suspend fun getThumbnail(file: FileItem): ThumbnailResult = withContext(Dispatchers.IO) {
        val viewerType = detectViewerType(file.name, file.mimeType)
        val key = thumbnailKey(file)
        val cacheFile = cacheFileFor(file, viewerType.cacheExtension())
        val memoryResult = memoryCache.get(key)

        when {
            file.isDirectory -> ThumbnailResult.Icon(IconResolver.resolve(file))
            memoryResult != null && cacheFile.exists() -> memoryResult
            cacheFile.exists() -> cachedFileResult(key, cacheFile)
            else -> ThumbnailResult.Icon(IconResolver.resolve(file))
        }
    }

    override suspend fun generateThumbnail(file: FileItem): ThumbnailResult = withContext(Dispatchers.IO) {
        val viewerType = detectViewerType(file.name, file.mimeType)

        if (file.sourceType != SourceType.LOCAL) {
            return@withContext ThumbnailResult.Icon(IconResolver.resolve(file))
        }

        runCatching {
            when (viewerType) {
                ViewerType.Pdf -> generatePdfThumbnail(file)
                ViewerType.Video -> generateVideoThumbnail(file)
                ViewerType.Image -> generateImageThumbnail(file)
                else -> ThumbnailResult.Icon(IconResolver.resolve(file))
            }
        }.getOrElse {
            ThumbnailResult.Icon(IconResolver.resolve(file))
        }
    }

    override fun requestThumbnail(file: FileItem) {
        if (file.isDirectory || file.sourceType != SourceType.LOCAL) {
            return
        }

        val viewerType = detectViewerType(file.name, file.mimeType)
        if (!viewerType.supportsGeneratedThumbnail()) {
            return
        }

        repositoryScope.launch {
            val key = thumbnailKey(file)
            val cacheFile = cacheFileFor(file, viewerType.cacheExtension())
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
                    QueuedThumbnailRequest(
                        file = file,
                        key = key,
                    ),
                )
            }
        }
    }

    override fun observeThumbnailVersion(): StateFlow<Long> = thumbnailVersion.asStateFlow()

    override suspend fun clearThumbnailCache() {
        withContext(Dispatchers.IO) {
            thumbnailDir.deleteRecursively()
            thumbnailDir.mkdirs()
        }
        memoryCache.evictAll()
        notifyThumbnailUpdated()
    }

    private suspend fun processThumbnailRequests(
        requests: Channel<QueuedThumbnailRequest>,
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

    private fun notifyThumbnailUpdated() {
        updateSignal.trySend(Unit)
    }

    private fun ViewerType.requestChannel(): Channel<QueuedThumbnailRequest> = when (this) {
        ViewerType.Pdf -> pdfRequests
        ViewerType.Image -> imageRequests
        ViewerType.Video -> videoRequests
        else -> error("Unsupported thumbnail type: $this")
    }

    private suspend fun generatePdfThumbnail(file: FileItem): ThumbnailResult = pdfRenderSemaphore.withPermit {
        val uri = Uri.parse(file.uri ?: return ThumbnailResult.Icon(IconResolver.resolve(file)))
        val cacheFile = cacheFileFor(file, "jpg")

        thumbnailDir.mkdirs()
        file.openThumbnailFileDescriptor(uri)?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) {
                    return ThumbnailResult.Unavailable("PDF has no pages.")
                }

                renderer.openPage(0).use { page ->
                    val bitmap = page.createScaledBitmap()
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.writeJpeg(cacheFile)
                    bitmap.recycle()
                }
            }
        } ?: return ThumbnailResult.Unavailable("PDF file descriptor could not be opened.")

        return ThumbnailResult.CachedFile(
            key = thumbnailKey(file),
            uri = Uri.fromFile(cacheFile).toString(),
        )
    }

    private suspend fun generateImageThumbnail(file: FileItem): ThumbnailResult = imageDecodeSemaphore.withPermit {
        generateImageThumbnailInternal(file)
    }

    private fun generateImageThumbnailInternal(file: FileItem): ThumbnailResult {
        val uri = Uri.parse(file.uri ?: return ThumbnailResult.Icon(IconResolver.resolve(file)))
        val cacheFile = cacheFileFor(file, "jpg")

        thumbnailDir.mkdirs()
        val sourceBitmap = decodeSampledImage(file, uri)
            ?: return ThumbnailResult.Unavailable("Image thumbnail could not be decoded.")
        val thumbnailBitmap = sourceBitmap.scaleToThumbnail()
        val outputBitmap = thumbnailBitmap.withWhiteBackground()
        outputBitmap.writeJpeg(cacheFile)

        if (outputBitmap !== thumbnailBitmap) {
            outputBitmap.recycle()
        }
        if (thumbnailBitmap !== sourceBitmap) {
            thumbnailBitmap.recycle()
        }
        sourceBitmap.recycle()

        return ThumbnailResult.CachedFile(
            key = thumbnailKey(file),
            uri = Uri.fromFile(cacheFile).toString(),
        )
    }

    private suspend fun generateVideoThumbnail(file: FileItem): ThumbnailResult = videoRenderSemaphore.withPermit {
        generateVideoThumbnailInternal(file)
    }

    private fun generateVideoThumbnailInternal(file: FileItem): ThumbnailResult {
        val uri = Uri.parse(file.uri ?: return ThumbnailResult.Icon(IconResolver.resolve(file)))
        val cacheFile = cacheFileFor(file, "jpg")

        thumbnailDir.mkdirs()
        val retriever = MediaMetadataRetriever()
        try {
            val localFile = file.localReadableFile()
            if (localFile != null) {
                retriever.setDataSource(localFile.path)
            } else {
                retriever.setDataSource(appContext, uri)
            }
            val bitmap = retriever.getThumbnailFrame()
                ?: return ThumbnailResult.Unavailable("Video frame could not be decoded.")
            bitmap.writeJpeg(cacheFile)
            bitmap.recycle()
        } finally {
            retriever.release()
        }

        return ThumbnailResult.CachedFile(
            key = thumbnailKey(file),
            uri = Uri.fromFile(cacheFile).toString(),
        )
    }

    private fun PdfRenderer.Page.createScaledBitmap(): Bitmap {
        val scale = min(
            MAX_THUMBNAIL_SIZE.toFloat() / width.toFloat(),
            MAX_THUMBNAIL_SIZE.toFloat() / height.toFloat(),
        ).coerceAtMost(1f)
        val bitmapWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val bitmapHeight = (height * scale).roundToInt().coerceAtLeast(1)

        return Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    }

    private fun FileItem.openThumbnailFileDescriptor(uri: Uri): ParcelFileDescriptor? {
        val localFile = localReadableFile()
        if (localFile != null) {
            return ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        return appContext.contentResolver.openFileDescriptor(uri, "r")
    }

    private fun decodeSampledImage(
        file: FileItem,
        uri: Uri,
    ): Bitmap? {
        val localFile = file.localReadableFile()
        return if (localFile != null) {
            decodeSampledFile(localFile)
        } else {
            decodeSampledStream(uri)
        }
    }

    private fun decodeSampledFile(file: File): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.path, boundsOptions)

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            return null
        }

        val decodeOptions = thumbnailDecodeOptions(
            width = boundsOptions.outWidth,
            height = boundsOptions.outHeight,
        )

        return BitmapFactory.decodeFile(file.path, decodeOptions)
    }

    private fun decodeSampledStream(uri: Uri): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        }

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            return null
        }

        val decodeOptions = thumbnailDecodeOptions(
            width = boundsOptions.outWidth,
            height = boundsOptions.outHeight,
        )

        return appContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        }
    }

    private fun thumbnailDecodeOptions(
        width: Int,
        height: Int,
    ): BitmapFactory.Options =
        BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                width = width,
                height = height,
                maxSize = MAX_THUMBNAIL_SIZE,
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

    private fun FileItem.localReadableFile(): File? {
        val parsedUri = uri
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val candidate = when (parsedUri?.scheme) {
            "file" -> parsedUri.path?.let(::File)
            null -> File(path)
            else -> null
        } ?: return null

        return candidate.takeIf { it.isFile && it.canRead() }
    }

    private fun MediaMetadataRetriever.getThumbnailFrame(): Bitmap? {
        val targetSize = videoTargetSize()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && targetSize != null) {
            return getScaledFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                targetSize.first,
                targetSize.second,
            )
        }

        val sourceBitmap = getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: return null
        val thumbnailBitmap = sourceBitmap.scaleToThumbnail()
        if (thumbnailBitmap !== sourceBitmap) {
            sourceBitmap.recycle()
        }
        return thumbnailBitmap
    }

    private fun MediaMetadataRetriever.videoTargetSize(): Pair<Int, Int>? {
        val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
            ?: return null
        val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
            ?: return null
        val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?: 0
        val sourceWidth = if (rotation == 90 || rotation == 270) height else width
        val sourceHeight = if (rotation == 90 || rotation == 270) width else height
        val target = scaledSize(
            width = sourceWidth,
            height = sourceHeight,
            maxSize = MAX_THUMBNAIL_SIZE,
        )
        return target.first.coerceAtLeast(1) to target.second.coerceAtLeast(1)
    }

    private fun Bitmap.scaleToThumbnail(): Bitmap {
        val (targetWidth, targetHeight) = scaledSize(width, height, MAX_THUMBNAIL_SIZE)
        if (targetWidth == width && targetHeight == height) {
            return this
        }

        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun Bitmap.withWhiteBackground(): Bitmap {
        if (!hasAlpha()) {
            return this
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            drawColor(Color.WHITE)
            drawBitmap(this@withWhiteBackground, 0f, 0f, null)
        }
        return output
    }

    private fun Bitmap.writeJpeg(file: File) {
        FileOutputStream(file).use { output ->
            compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
    }

    private fun cacheFileFor(file: FileItem, extension: String): File {
        val key = thumbnailKey(file).sha256()
        return File(thumbnailDir, "$key.$extension")
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
        const val MAX_THUMBNAIL_SIZE = 240
        const val JPEG_QUALITY = 72
        const val IMAGE_WORKER_COUNT = 2
        const val MEMORY_CACHE_SIZE = 512
        const val THUMBNAIL_UPDATE_THROTTLE_MS = 120L
        val pdfRenderSemaphore = Semaphore(1)
        val videoRenderSemaphore = Semaphore(1)
        val imageDecodeSemaphore = Semaphore(2)
    }
}

private data class QueuedThumbnailRequest(
    val file: FileItem,
    val key: String,
)

private fun ViewerType.cacheExtension(): String = when (this) {
    ViewerType.Pdf -> "jpg"
    ViewerType.Image -> "jpg"
    ViewerType.Video -> "jpg"
    else -> "png"
}

private fun ViewerType.supportsGeneratedThumbnail(): Boolean =
    this == ViewerType.Pdf || this == ViewerType.Image || this == ViewerType.Video

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    maxSize: Int,
): Int {
    var sampleSize = 1
    while (width / (sampleSize * 2) >= maxSize || height / (sampleSize * 2) >= maxSize) {
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

private fun scaledSize(
    width: Int,
    height: Int,
    maxSize: Int,
): Pair<Int, Int> {
    val scale = min(
        maxSize.toFloat() / width.toFloat(),
        maxSize.toFloat() / height.toFloat(),
    ).coerceAtMost(1f)
    return (width * scale).roundToInt().coerceAtLeast(1) to
        (height * scale).roundToInt().coerceAtLeast(1)
}
