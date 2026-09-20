package com.ryo.androidfilemanager.viewer

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ryo.androidfilemanager.core.domain.OpenedFile
import com.ryo.androidfilemanager.ui.components.ScrollbarMetrics
import com.ryo.androidfilemanager.ui.components.VerticalScrollbar
import com.ryo.androidfilemanager.ui.components.pixelScrollbarMetrics
import com.ryo.androidfilemanager.ui.components.pixelScrollbarTargetOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

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

    val viewerState by produceState<PdfViewerState>(
        initialValue = PdfViewerState.Loading,
        key1 = uri,
    ) {
        value = try {
            PdfViewerState.Ready(PdfDocumentRenderer.open(context, uri))
        } catch (e: PdfOpenException) {
            PdfViewerState.Error(e.message ?: "The PDF could not be opened.")
        } catch (e: Exception) {
            PdfViewerState.Error("The PDF file is damaged or is not a valid PDF.")
        }
    }

    // onDispose で viewerState を読み直すと、Loading -> Ready に切り替わった瞬間の
    // 破棄処理が「開いたばかりの」レンダラーを閉じてしまう。効果の中で値を固定する
    DisposableEffect(viewerState) {
        val stateAtEffect = viewerState
        onDispose {
            (stateAtEffect as? PdfViewerState.Ready)?.renderer?.close()
        }
    }

    when (val state = viewerState) {
        PdfViewerState.Loading -> PdfLoadingMessage(modifier = modifier)
        is PdfViewerState.Error -> PdfErrorMessage(message = state.message, modifier = modifier)
        is PdfViewerState.Ready -> PdfReadyContent(renderer = state.renderer, modifier = modifier)
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun PdfReadyContent(
    renderer: PdfDocumentRenderer,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    var scale by rememberSaveable { mutableFloatStateOf(PDF_MIN_SCALE) }
    val scrollScope = rememberCoroutineScope()
    var isScrollbarDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // PDF ページは常に白いので、background だと同色になり境界が消えるライトテーマがある。
            // 一段濃い surfaceContainerHighest でページとの境界を保つ
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(Unit) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                scale = clampPdfScale(scale * zoom)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }.roundToInt()

        var settledRenderWidthPx by remember(renderer) { mutableStateOf(0) }
        LaunchedEffect(renderer, viewportWidthPx) {
            if (viewportWidthPx <= 0) return@LaunchedEffect
            val renderWidthFlow = snapshotFlow { pdfRenderWidthPx(viewportWidthPx, scale) }
                .distinctUntilChanged()
            // 最初の値は即時反映し、以降のズーム操作はデバウンスして再描画頻度を抑える。
            merge(renderWidthFlow.take(1), renderWidthFlow.drop(1).debounce(250))
                .distinctUntilChanged()
                .collect { settledRenderWidthPx = it }
        }

        val columnWidthDp = with(density) { (viewportWidthPx * scale).roundToInt().toDp() }

        Box(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .width(columnWidthDp)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    count = renderer.pageCount,
                    key = { pageIndex -> pageIndex },
                ) { pageIndex ->
                    PdfPageItem(
                        renderer = renderer,
                        pageIndex = pageIndex,
                        settledRenderWidthPx = settledRenderWidthPx,
                        scale = scale,
                        onScaleChange = { scale = it },
                    )
                }
            }
        }

        // horizontalScroll の Box の外側に置くことで、横スクロールしても
        // 常に画面右端に固定されるようにする（ズームで横に広がったコンテンツと一緒に動かない）
        val scrollbarMetrics by remember(listState) {
            derivedStateOf { listState.pixelScrollbarMetricsOrNull() }
        }
        VerticalScrollbar(
            metrics = scrollbarMetrics,
            onDragFraction = { fraction ->
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                val firstVisible = visibleItems.firstOrNull()
                if (firstVisible == null) return@VerticalScrollbar
                val averageItemHeightPx = visibleItems.map { it.size }.average().toFloat()
                val itemSpacingPx = layoutInfo.mainAxisItemSpacing.toFloat()
                val itemCount = layoutInfo.totalItemsCount

                val estimated = estimateListScroll(
                    firstVisibleIndex = firstVisible.index,
                    firstVisibleScrollOffsetPx = listState.firstVisibleItemScrollOffset,
                    averageItemHeightPx = averageItemHeightPx,
                    itemSpacingPx = itemSpacingPx,
                    itemCount = itemCount,
                    contentPaddingPx = layoutInfo.beforeContentPadding.toFloat(),
                )
                val targetOffsetPx = pixelScrollbarTargetOffset(
                    positionFraction = fraction,
                    contentHeightPx = estimated.contentHeightPx,
                    viewportHeightPx = layoutInfo.viewportSize.height.toFloat(),
                )
                val target = listScrollTargetForOffset(
                    offsetPx = targetOffsetPx,
                    averageItemHeightPx = averageItemHeightPx,
                    itemSpacingPx = itemSpacingPx,
                    itemCount = itemCount,
                )
                scrollScope.launch {
                    listState.scrollToItem(target.index, target.scrollOffsetPx)
                }
            },
            onDraggingChange = { dragging -> isScrollbarDragging = dragging },
            autoHide = true,
            scrollInProgress = listState.isScrollInProgress,
            // PDF の紙面はテーマに関係なく常に白いため、テーマ色（ダークテーマでは
            // onSurfaceVariant がほぼ見えなくなる）ではなく紙面に対して常に見える固定色を使う。
            // これは CLAUDE.md の「Color(0x...) 直書き禁止」の UI 規約の例外として明記する
            thumbColor = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier.align(Alignment.CenterEnd),
        )

        PdfPageIndicatorPill(
            listState = listState,
            pageCount = renderer.pageCount,
            isScrollbarDragging = isScrollbarDragging,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        )
    }
}

/**
 * [estimateListScroll] を使って現在のスクロール位置・コンテンツ高さを推定し、
 * [pixelScrollbarMetrics] に変換する。ページ高さの推定方式は estimateListScroll の
 * KDoc（Why not）を参照。
 */
private fun LazyListState.pixelScrollbarMetricsOrNull(): ScrollbarMetrics? {
    val visibleItems = layoutInfo.visibleItemsInfo
    val firstVisible = visibleItems.firstOrNull() ?: return null
    val averageItemHeightPx = visibleItems.map { it.size }.average().toFloat()

    val estimated = estimateListScroll(
        firstVisibleIndex = firstVisible.index,
        firstVisibleScrollOffsetPx = firstVisibleItemScrollOffset,
        averageItemHeightPx = averageItemHeightPx,
        itemSpacingPx = layoutInfo.mainAxisItemSpacing.toFloat(),
        itemCount = layoutInfo.totalItemsCount,
        contentPaddingPx = layoutInfo.beforeContentPadding.toFloat(),
    )

    return pixelScrollbarMetrics(
        scrollOffsetPx = estimated.offsetPx,
        contentHeightPx = estimated.contentHeightPx,
        viewportHeightPx = layoutInfo.viewportSize.height.toFloat(),
    )
}

@Composable
private fun PdfPageItem(
    renderer: PdfDocumentRenderer,
    pageIndex: Int,
    settledRenderWidthPx: Int,
    scale: Float,
    onScaleChange: (Float) -> Unit,
) {
    val pageSize = renderer.pageSizes[pageIndex]
    var bitmap by remember(renderer, pageIndex) { mutableStateOf<Bitmap?>(null) }
    // pointerInput は scale をキーにしていないので、ダブルタップ時は最新の倍率を読む
    val latestScale by rememberUpdatedState(scale)

    LaunchedEffect(renderer, pageIndex, settledRenderWidthPx) {
        if (settledRenderWidthPx > 0) {
            // 失敗時は直前のビットマップ（あれば）を残す
            renderer.render(pageIndex, settledRenderWidthPx)?.let { bitmap = it }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(pageSize.width.toFloat() / pageSize.height.toFloat())
            .background(Color.White)
            .pointerInput(renderer, pageIndex) {
                detectTapGestures(
                    onDoubleTap = {
                        onScaleChange(if (latestScale > PDF_MIN_SCALE) PDF_MIN_SCALE else 2f)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = "PDF page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillWidth,
            )
        } else {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PdfPageIndicatorPill(
    listState: LazyListState,
    pageCount: Int,
    isScrollbarDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentPageIndex by remember(pageCount) {
        derivedStateOf {
            pdfCurrentPageIndex(
                firstVisibleIndex = listState.firstVisibleItemIndex,
                firstVisibleScrollOffsetPx = listState.firstVisibleItemScrollOffset,
                firstVisibleItemHeightPx = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0,
                pageCount = pageCount,
            )
        }
    }
    var pillVisible by remember { mutableStateOf(true) }

    // スクロールバーのドラッグ中もページ番号が分かるようにする（isScrollInProgress は
    // scrollToItem によるジャンプ中しか true にならず、ドラッグ操作自体では立たない）
    LaunchedEffect(listState.isScrollInProgress, isScrollbarDragging) {
        if (listState.isScrollInProgress || isScrollbarDragging) {
            pillVisible = true
        } else {
            pillVisible = true
            delay(1200)
            pillVisible = false
        }
    }

    AnimatedVisibility(visible = pillVisible, modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = "${currentPageIndex + 1} / $pageCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PdfLoadingMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(text = "Loading PDF...")
        }
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private sealed class PdfViewerState {
    data object Loading : PdfViewerState()

    data class Ready(val renderer: PdfDocumentRenderer) : PdfViewerState()

    data class Error(val message: String) : PdfViewerState()
}
