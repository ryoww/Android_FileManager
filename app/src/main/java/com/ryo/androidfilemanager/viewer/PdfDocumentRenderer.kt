package com.ryo.androidfilemanager.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

/** PDF を開けなかった/描画できなかったことを表す例外。message は画面にそのまま表示できる文言。 */
internal class PdfOpenException(message: String) : Exception(message)

/**
 * ドキュメント単位で 1 つの [ParcelFileDescriptor] + [PdfRenderer] を保持するクラス。
 * ページを開き直すたびに fd を再取得していた旧実装に対し、開いた状態を使い回して高速化する。
 */
internal class PdfDocumentRenderer private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageSizes: List<IntSize>,
) : AutoCloseable {

    val pageCount: Int get() = pageSizes.size

    // PdfRenderer はスレッドセーフでないため、ページを開く/描画する操作は直列化する。
    private val renderMutex = Mutex()
    private val bitmapCache = object : LinkedHashMap<PageCacheKey, Bitmap>(16, 0.75f, true) {
        // 追い出したビットマップは recycle() しない。表示中の Image がまだ参照している
        // 可能性があり、recycle 済みビットマップの描画はクラッシュするため GC に任せる
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PageCacheKey, Bitmap>): Boolean =
            cacheSizeBytes() > MAX_CACHE_BYTES
    }

    private fun cacheSizeBytes(): Long = bitmapCache.values.sumOf { it.byteCount.toLong() }

    @Volatile
    private var closed = false

    /** 描画に失敗した、または close 済みなら null。呼び出し側はプレースホルダのままにする。 */
    suspend fun render(pageIndex: Int, targetWidthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = PageCacheKey(pageIndex, targetWidthPx)
        renderMutex.withLock {
            if (closed) return@withContext null
            bitmapCache[key]?.let { return@withContext it }

            val pageSize = pageSizes[pageIndex]
            val scale = targetWidthPx.toFloat() / pageSize.width.toFloat()
            val bitmapWidth = targetWidthPx.coerceAtLeast(1)
            val bitmapHeight = (pageSize.height * scale).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            val rendered = runCatching {
                renderer.openPage(pageIndex).use { page ->
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }.isSuccess
            if (!rendered) {
                return@withContext null
            }
            bitmapCache[key] = bitmap
            bitmap
        }
    }

    // 進行中の描画が PdfRenderer を触っている最中に閉じると IllegalStateException で
    // 落ちるため、mutex を取ってから閉じる（描画 1 回分だけ待つ）
    override fun close() {
        closed = true
        runBlocking {
            renderMutex.withLock {
                bitmapCache.clear()
                renderer.close()
                descriptor.close()
            }
        }
    }

    private data class PageCacheKey(val pageIndex: Int, val targetWidthPx: Int)

    companion object {
        private const val MAX_CACHE_BYTES = 96L * 1024 * 1024

        suspend fun open(context: Context, uri: Uri): PdfDocumentRenderer = withContext(Dispatchers.IO) {
            val descriptor = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (e: SecurityException) {
                throw PdfOpenException("This PDF is password-protected and cannot be displayed.")
            } catch (e: IOException) {
                throw PdfOpenException(
                    "The PDF could not be opened. The file may have been moved or the permission expired.",
                )
            } ?: throw PdfOpenException(
                "The PDF could not be opened. The file may have been moved or the permission expired.",
            )

            val renderer = try {
                PdfRenderer(descriptor)
            } catch (e: SecurityException) {
                descriptor.close()
                throw PdfOpenException("This PDF is password-protected and cannot be displayed.")
            } catch (e: IOException) {
                descriptor.close()
                throw PdfOpenException("The PDF file is damaged or is not a valid PDF.")
            } catch (e: IllegalArgumentException) {
                descriptor.close()
                throw PdfOpenException("The PDF file is damaged or is not a valid PDF.")
            }

            if (renderer.pageCount == 0) {
                renderer.close()
                descriptor.close()
                throw PdfOpenException("This PDF has no pages.")
            }

            val pageSizes = (0 until renderer.pageCount).map { pageIndex ->
                renderer.openPage(pageIndex).use { page ->
                    IntSize(page.width, page.height)
                }
            }

            PdfDocumentRenderer(descriptor, renderer, pageSizes)
        }
    }
}
