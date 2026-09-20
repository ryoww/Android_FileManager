package com.ryo.androidfilemanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 右端に張り付くドラッグ可能な縦スクロールバー。エクスプローラー一覧・PDF ビューワーなど
 * 「スクロール位置を 0f..1f の割合で扱える画面」から共通で使う。
 *
 * @param metrics サムの位置・高さ。null なら何も描かずタッチも無効にする（＝コンテンツが
 * ビューポートに収まっていてスクロール自体が不要な状態）。
 * @param onDragFraction ドラッグ/タップ位置から求めた 0f..1f の positionFraction
 * （サム中心基準）。index や px への変換は呼び出し側の責務にする。呼び出し側ごとに
 * 「リストの何番目か」「ピクセルオフセットか」が異なるため、ここでは正規化した割合だけを渡す。
 * @param autoHide true の場合、指標が変化せずドラッグもしていない状態が続くとフェードアウトする。
 * フェードアウト中・後はタッチに一切反応しない（Android 標準のファストスクローラーと同じで、
 * 一度スクロールしてバーを出してから掴む）。
 * @param scrollInProgress 対象リストがスクロール中なら true。autoHide のとき、先頭 index が
 * 変わらない小さなスクロールでも指標は変化しないため、これを見て再表示する。
 * @param thumbColor サムの色。省略時はテーマの onSurfaceVariant。PDF ビューワーのように
 * 背景がテーマに関係なく常に白い画面では、テーマ色だと視認できないことがあるため上書きできる。
 */
@Composable
internal fun VerticalScrollbar(
    metrics: ScrollbarMetrics?,
    onDragFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDraggingChange: ((Boolean) -> Unit)? = null,
    autoHide: Boolean = false,
    scrollInProgress: Boolean = false,
    thumbColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
) {
    val currentMetrics = metrics ?: return
    // ドラッグ中の onDragFraction 呼び出しで metrics が変わるたびに pointerInput が
    // 再起動するとジェスチャが中断されるため、キーは固定し最新値は State 経由で読む
    val latestMetrics = rememberUpdatedState(currentMetrics)
    val latestOnDragFraction = rememberUpdatedState(onDragFraction)
    var isDragging by remember { mutableStateOf(false) }

    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(autoHide, currentMetrics, isDragging, scrollInProgress) {
        if (!autoHide || isDragging || scrollInProgress) {
            visible = true
            return@LaunchedEffect
        }
        visible = true
        delay(AUTO_HIDE_DELAY_MS)
        visible = false
    }
    // AnimatedVisibility はレイアウトそのものを着脱するため、フェード中に再表示すると
    // 位置がずれる。alpha だけを animateFloatAsState で変化させ、Canvas 自体は常に配置する
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "scrollbarAlpha",
    )
    val draggingColor = MaterialTheme.colorScheme.primary

    // 隠れている間は pointerInput 自体を付けない。Compose は重なった兄弟のうち最前面で
    // ヒットした要素にだけタッチを配るので、consume しなくても Canvas に pointerInput が
    // ある限り下の一覧には届かず、右端の項目をタップしても開けなくなるため。
    // 可視状態の切り替えはドラッグ中には起きない（isDragging が visible を固定する）ので、
    // 付け外しでジェスチャが途中で切れることはない
    val dragModifier = if (visible) {
        Modifier.pointerInput(Unit) {
            val minThumbHeightPx = 28.dp.toPx()
            detectScrollbarDrag(
                metrics = { latestMetrics.value },
                minThumbHeightPx = minThumbHeightPx,
                onDragFraction = { fraction -> latestOnDragFraction.value(fraction) },
                onDraggingChange = { dragging ->
                    isDragging = dragging
                    onDraggingChange?.invoke(dragging)
                },
            )
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .padding(top = 6.dp, bottom = 6.dp, end = 2.dp)
            .then(dragModifier),
    ) {
        val thumbWidth = 4.dp.toPx()
        val thumbBounds = scrollbarThumbBounds(
            trackHeightPx = size.height,
            metrics = currentMetrics,
            minThumbHeightPx = 28.dp.toPx(),
        )
        val thumbHeight = thumbBounds.endInclusive - thumbBounds.start
        val thumbTop = thumbBounds.start
        val thumbLeft = (size.width - thumbWidth) / 2f

        val baseColor = if (isDragging) draggingColor else thumbColor
        drawRoundRect(
            color = baseColor.copy(alpha = baseColor.alpha * alpha),
            topLeft = Offset(thumbLeft, thumbTop),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f),
        )
    }
}

/**
 * スクロールバーのドラッグ検出。バーが可視のときはトラック内のどこでも掴める
 * （不可視のときはこの pointerInput 自体が付いていない）。
 *
 * Why not 「不可視でもサムの位置なら掴める」: 内容がわずかにはみ出すだけの一覧では
 * サムがトラックの大半を占めるので、結局ほとんどのタップを横取りしてしまう。
 * Android 標準のファストスクローラーと同じく、スクロールで出してから掴む。
 */
private suspend fun PointerInputScope.detectScrollbarDrag(
    metrics: () -> ScrollbarMetrics,
    minThumbHeightPx: Float,
    onDragFraction: (Float) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown()

        // consume しないと下の LazyList/LazyColumn も同じドラッグでスクロールを
        // 始めてしまい、スクロールバー操作と二重に動く
        down.consume()
        onDraggingChange(true)

        fun dragToTouch(touchY: Float) {
            val thumbBounds = scrollbarThumbBounds(
                trackHeightPx = size.height.toFloat(),
                metrics = metrics(),
                minThumbHeightPx = minThumbHeightPx,
            )
            val thumbHeight = thumbBounds.endInclusive - thumbBounds.start
            val fraction = scrollbarDragFraction(
                touchY = touchY,
                trackHeight = size.height.toFloat(),
                thumbHeight = thumbHeight,
            )
            onDragFraction(fraction)
        }

        dragToTouch(down.position.y)

        val pointerId = down.id
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (!change.pressed) {
                    break
                }
                change.consume()
                dragToTouch(change.position.y)
            }
        } finally {
            onDraggingChange(false)
        }
    }
}

private const val AUTO_HIDE_DELAY_MS = 1200L
