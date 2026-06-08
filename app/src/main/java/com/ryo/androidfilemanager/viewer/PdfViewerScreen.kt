package com.ryo.androidfilemanager.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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

    val document by produceState<PdfDocumentState>(
        initialValue = PdfDocumentState.Loading,
        key1 = uri,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        if (renderer.pageCount == 0) {
                            PdfDocumentState.Error("PDF has no pages.")
                        } else {
                            PdfDocumentState.Ready(
                                pageCount = renderer.pageCount,
                            )
                        }
                    }
                } ?: PdfDocumentState.Error("PDF file descriptor could not be opened.")
            }.getOrElse { throwable ->
                PdfDocumentState.Error(throwable.message ?: "PDF could not be opened.")
            }
        }
    }

    when (val state = document) {
        PdfDocumentState.Loading -> PdfLoadingMessage(
            message = "Loading PDF...",
            modifier = modifier,
        )

        is PdfDocumentState.Error -> PdfErrorMessage(
            message = state.message,
            modifier = modifier,
        )

        is PdfDocumentState.Ready -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = "${state.pageCount} pages",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(
                items = (0 until state.pageCount).toList(),
                key = { pageIndex -> "$uri-$pageIndex" },
            ) { pageIndex ->
                PdfPage(
                    uri = uri,
                    pageIndex = pageIndex,
                    pageCount = state.pageCount,
                )
            }
        }
    }
}

@Composable
private fun PdfPage(
    uri: android.net.Uri,
    pageIndex: Int,
    pageCount: Int,
) {
    val context = LocalContext.current
    val page by produceState<PdfPageRenderResult>(
        initialValue = PdfPageRenderResult.Loading,
        key1 = uri,
        key2 = pageIndex,
    ) {
        value = withContext(Dispatchers.IO) {
            renderPage(
                context = context,
                uri = uri,
                pageIndex = pageIndex,
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Page ${pageIndex + 1} / $pageCount",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val result = page) {
                PdfPageRenderResult.Loading -> Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is PdfPageRenderResult.Error -> Text(
                    text = result.message,
                    color = MaterialTheme.colorScheme.error,
                )

                is PdfPageRenderResult.Page -> {
                    DisposableEffect(result.bitmap) {
                        onDispose {
                            result.bitmap.recycle()
                        }
                    }
                    Image(
                        bitmap = result.bitmap.asImageBitmap(),
                        contentDescription = "PDF page ${result.pageIndex + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(result.aspectRatio),
                        contentScale = ContentScale.FillWidth,
                    )
                }
            }
        }
    }
}

private fun renderPage(
    context: android.content.Context,
    uri: android.net.Uri,
    pageIndex: Int,
): PdfPageRenderResult = runCatching {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val boundedIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(boundedIndex).use { pdfPage ->
                val scale = (PDF_RENDER_WIDTH.toFloat() / pdfPage.width.toFloat()).coerceAtMost(2f)
                val bitmap = Bitmap.createBitmap(
                    (pdfPage.width * scale).roundToInt().coerceAtLeast(1),
                    (pdfPage.height * scale).roundToInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.eraseColor(Color.WHITE)
                pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                PdfPageRenderResult.Page(
                    bitmap = bitmap,
                    pageIndex = boundedIndex,
                    aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat(),
                )
            }
        }
    } ?: PdfPageRenderResult.Error("PDF file descriptor could not be opened.")
}.getOrElse { throwable ->
    PdfPageRenderResult.Error(throwable.message ?: "PDF page could not be rendered.")
}

@Composable
private fun PdfLoadingMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message)
    }
}

@Composable
private fun PdfErrorMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private sealed class PdfDocumentState {
    data object Loading : PdfDocumentState()

    data class Ready(
        val pageCount: Int,
    ) : PdfDocumentState()

    data class Error(
        val message: String,
    ) : PdfDocumentState()
}

private sealed class PdfPageRenderResult {
    data object Loading : PdfPageRenderResult()

    data class Page(
        val bitmap: Bitmap,
        val pageIndex: Int,
        val aspectRatio: Float,
    ) : PdfPageRenderResult()

    data class Error(
        val message: String,
    ) : PdfPageRenderResult()
}

private const val PDF_RENDER_WIDTH = 1200
