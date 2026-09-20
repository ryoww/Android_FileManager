package com.ryo.androidfilemanager.data.thumbnail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * PDF 1ページ目をサムネイル画像としてレンダリングする。
 * PdfRenderer はスレッドセーフではないため、プロセス内で直列化する。
 * 渡された descriptor はこの関数が必ずクローズする(PdfRenderer が所有権を取る)。
 */
object PdfThumbnailGenerator {
    private val renderSemaphore = Semaphore(1)

    suspend fun renderFirstPage(
        descriptor: ParcelFileDescriptor,
        output: File,
        maxSize: Int,
        jpegQuality: Int,
    ): Boolean = renderSemaphore.withPermit {
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (throwable: Throwable) {
            runCatching { descriptor.close() }
            throw throwable
        }

        renderer.use {
            if (it.pageCount == 0) {
                return@withPermit false
            }

            it.openPage(0).use { page ->
                val scale = min(
                    maxSize.toFloat() / page.width.toFloat(),
                    maxSize.toFloat() / page.height.toFloat(),
                )
                val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                output.parentFile?.mkdirs()
                FileOutputStream(output).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
                }
            }
        }
        true
    }
}
