package com.ryo.androidfilemanager.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.SourceType
import com.ryo.androidfilemanager.data.model.ViewerType
import com.ryo.androidfilemanager.data.source.detectViewerType
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileThumbnailRepository(
    context: Context,
) : ThumbnailRepository {
    private val appContext = context.applicationContext
    private val thumbnailDir = File(appContext.cacheDir, "thumbnails")

    override suspend fun getThumbnail(file: FileItem): ThumbnailResult = withContext(Dispatchers.IO) {
        val viewerType = detectViewerType(file.name, file.mimeType)
        val cacheFile = cacheFileFor(file)

        when {
            file.isDirectory -> ThumbnailResult.Icon(IconResolver.resolve(file))
            cacheFile.exists() -> ThumbnailResult.CachedFile(
                key = thumbnailKey(file),
                uri = Uri.fromFile(cacheFile).toString(),
            )
            viewerType == ViewerType.Image && file.uri != null -> ThumbnailResult.CachedFile(
                key = thumbnailKey(file),
                uri = file.uri,
            )
            else -> generateThumbnail(file)
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
                ViewerType.Image -> file.uri?.let {
                    ThumbnailResult.CachedFile(
                        key = thumbnailKey(file),
                        uri = it,
                    )
                } ?: ThumbnailResult.Icon(IconResolver.resolve(file))
                else -> ThumbnailResult.Icon(IconResolver.resolve(file))
            }
        }.getOrElse {
            ThumbnailResult.Icon(IconResolver.resolve(file))
        }
    }

    override suspend fun clearThumbnailCache() {
        withContext(Dispatchers.IO) {
        thumbnailDir.deleteRecursively()
        thumbnailDir.mkdirs()
        }
    }

    private fun generatePdfThumbnail(file: FileItem): ThumbnailResult {
        val uri = Uri.parse(file.uri ?: return ThumbnailResult.Icon(IconResolver.resolve(file)))
        val cacheFile = cacheFileFor(file)

        thumbnailDir.mkdirs()
        appContext.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) {
                    return ThumbnailResult.Unavailable("PDF has no pages.")
                }

                renderer.openPage(0).use { page ->
                    val bitmap = page.createScaledBitmap()
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.writePng(cacheFile)
                    bitmap.recycle()
                }
            }
        } ?: return ThumbnailResult.Unavailable("PDF file descriptor could not be opened.")

        return ThumbnailResult.CachedFile(
            key = thumbnailKey(file),
            uri = Uri.fromFile(cacheFile).toString(),
        )
    }

    private fun generateVideoThumbnail(file: FileItem): ThumbnailResult {
        val uri = Uri.parse(file.uri ?: return ThumbnailResult.Icon(IconResolver.resolve(file)))
        val cacheFile = cacheFileFor(file)

        thumbnailDir.mkdirs()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return ThumbnailResult.Unavailable("Video frame could not be decoded.")
            bitmap.writePng(cacheFile)
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

    private fun Bitmap.writePng(file: File) {
        FileOutputStream(file).use { output ->
            compress(Bitmap.CompressFormat.PNG, 90, output)
        }
    }

    private fun cacheFileFor(file: FileItem): File {
        val key = thumbnailKey(file).sha256()
        return File(thumbnailDir, "$key.png")
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val MAX_THUMBNAIL_SIZE = 512
    }
}
