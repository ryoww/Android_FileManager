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
 */
@Composable
internal fun VerticalScrollbar(
    metrics: ScrollbarMetrics?,
    onDragFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDraggingChange: ((Boolean) -> Unit)? = null,
    autoHide: Boolean = false,
) {
    val currentMetrics = metrics ?: return
    // ドラッグ中の onDragFraction 呼び出しで metrics が変わるたびに pointerInput が
    // 再起動するとジェスチャが中断されるため、キーは固定し最新値は State 経由で読む
    val latestMetrics = rememberUpdatedState(currentMetrics)
    val latestOnDragFraction = rememberUpdatedState(onDragFraction)
    var isDragging by remember { mutableStateOf(false) }

    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(autoHide, currentMetrics, isDragging) {
        if (!autoHide || isDragging) {
            visible = true
            return@LaunchedEffect
        }
        visible = true
        delay(AUTO_HIDE_DELAY_MS)
        visible = false
    }
    // AnimatedVisibility はレイアウトそのものを着脱するため pointerInput が切れ、
    // フェード中のドラッグ開始を取りこぼす。alpha だけを animateFloatAsState で
    // 変化させ、Canvas 自体は常に配置してタッチを受け付け続ける
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "scrollbarAlpha",
    )

    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val draggingColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .padding(top = 6.dp, bottom = 6.dp, end = 2.dp)
            .pointerInput(Unit) {
                detectScrollbarDrag(
                    metrics = { latestMetrics.value },
                    onDragFraction = { fraction -> latestOnDragFraction.value(fraction) },
                    onDraggingChange = { dragging ->
                        isDragging = dragging
                        onDraggingChange?.invoke(dragging)
                    },
                )
            },
    ) {
        val thumbWidth = 4.dp.toPx()
        val thumbHeight = (size.height * currentMetrics.thumbHeightFraction)
            .coerceAtLeast(28.dp.toPx())
            .coerceAtMost(size.height)
        val thumbTop = (size.height - thumbHeight) * currentMetrics.positionFraction
        val thumbLeft = (size.width - thumbWidth) / 2f

        val baseColor = if (isDragging) draggingColor else trackColor
        drawRoundRect(
            color = baseColor.copy(alpha = baseColor.alpha * alpha),
            topLeft = Offset(thumbLeft, thumbTop),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f),
        )
    }
}

private suspend fun PointerInputScope.detectScrollbarDrag(
    metrics: () -> ScrollbarMetrics,
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
            val thumbHeight = (size.height * metrics().thumbHeightFraction)
                .coerceAtLeast(28.dp.toPx())
                .coerceAtMost(size.height.toFloat())
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
