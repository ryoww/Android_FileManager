package com.ryo.androidfilemanager.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ryo.androidfilemanager.data.model.OpenedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun PdfViewerScreen(
    openedFile: OpenedFile,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uri = openedFile.localUriOrNull()

    if (uri == null) {
        StreamUnsupportedPlaceholder(openedFile = openedFile, modifier = modifier)
        return
    }

    var pageIndex by remember(uri) { mutableIntStateOf(0) }
    val page by produceState<PdfPageRenderResult>(
        initialValue = PdfPageRenderResult.Loading,
        key1 = uri,
        key2 = pageIndex,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        if (renderer.pageCount == 0) {
                            return@withContext PdfPageRenderResult.Error("PDF has no pages.")
                        }

                        val boundedIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                        renderer.openPage(boundedIndex).use { pdfPage ->
                            val scale = (PDF_RENDER_WIDTH.toFloat() / pdfPage.width.toFloat()).coerceAtMost(2f)
                            val bitmap = Bitmap.createBitmap(
                                (pdfPage.width * scale).roundToInt().coerceAtLeast(1),
                                (pdfPage.height * scale).roundToInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            PdfPageRenderResult.Page(
                                bitmap = bitmap,
                                pageIndex = boundedIndex,
                                pageCount = renderer.pageCount,
                            )
                        }
                    }
                } ?: PdfPageRenderResult.Error("PDF file descriptor could not be opened.")
            }.getOrElse { throwable ->
                PdfPageRenderResult.Error(throwable.message ?: "PDF could not be rendered.")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val result = page) {
            PdfPageRenderResult.Loading -> Text(text = "Loading PDF...")
            is PdfPageRenderResult.Error -> Text(
                text = result.message,
                color = MaterialTheme.colorScheme.error,
            )

            is PdfPageRenderResult.Page -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                        enabled = result.pageIndex > 0,
                    ) {
                        Text(text = "Prev")
                    }
                    Text(text = "${result.pageIndex + 1} / ${result.pageCount}")
                    Button(
                        onClick = { pageIndex = (pageIndex + 1).coerceAtMost(result.pageCount - 1) },
                        enabled = result.pageIndex < result.pageCount - 1,
                    ) {
                        Text(text = "Next")
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = result.bitmap.asImageBitmap(),
                        contentDescription = "PDF page ${result.pageIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

private sealed class PdfPageRenderResult {
    data object Loading : PdfPageRenderResult()

    data class Page(
        val bitmap: Bitmap,
        val pageIndex: Int,
        val pageCount: Int,
    ) : PdfPageRenderResult()

    data class Error(
        val message: String,
    ) : PdfPageRenderResult()
}

private const val PDF_RENDER_WIDTH = 1200
