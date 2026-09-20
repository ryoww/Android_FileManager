package com.ryo.androidfilemanager.ui.components

import kotlin.math.round

/**
 * スクロールバーのサム位置・高さを表す純データ。
 * Android 依存を持たないため JVM ユニットテストが可能。
 */
internal data class ScrollbarMetrics(
    val positionFraction: Float,
    val thumbHeightFraction: Float,
)

/**
 * 現在の可視範囲からスクロールバーのサム位置・高さを算出する。
 */
internal fun scrollbarMetrics(
    firstVisibleIndex: Int,
    visibleItemCount: Int,
    totalItemCount: Int,
): ScrollbarMetrics {
    val maxFirstVisibleIndex = (totalItemCount - visibleItemCount).coerceAtLeast(1)
    val thumbHeightFraction = (visibleItemCount.toFloat() / totalItemCount.toFloat())
        .coerceIn(0.08f, 1f)
    val positionFraction = (firstVisibleIndex.toFloat() / maxFirstVisibleIndex.toFloat())
        .coerceIn(0f, 1f)

    return ScrollbarMetrics(
        positionFraction = positionFraction,
        thumbHeightFraction = thumbHeightFraction,
    )
}

/**
 * ドラッグ/タップ位置（0f..1f の positionFraction）から `scrollToItem` に渡す
 * 先頭アイテム index を算出する。[scrollbarMetrics] の positionFraction と往復整合する。
 */
internal fun scrollbarTargetIndex(
    positionFraction: Float,
    totalItemCount: Int,
    visibleItemCount: Int,
): Int {
    val maxFirstVisibleIndex = totalItemCount - visibleItemCount
    if (maxFirstVisibleIndex <= 0) {
        return 0
    }

    val clampedFraction = positionFraction.coerceIn(0f, 1f)
    val target = round(clampedFraction * maxFirstVisibleIndex).toInt()
    return target.coerceIn(0, maxFirstVisibleIndex)
}

/**
 * スクロールバーのタッチ Y 座標から positionFraction（0f..1f）を算出する。
 * サムの中心がタッチ位置に来るように、サム高さの半分をオフセットする。
 */
internal fun scrollbarDragFraction(
    touchY: Float,
    trackHeight: Float,
    thumbHeight: Float,
): Float {
    val travel = trackHeight - thumbHeight
    if (travel <= 0f) {
        return 0f
    }

    val fraction = (touchY - thumbHeight / 2f) / travel
    return fraction.coerceIn(0f, 1f)
}

/**
 * ピクセル基準（スクロールオフセット・コンテンツ高さ）でのスクロールバー指標。
 * PDF ビューワーのように「アイテム数」ではなく実測ピクセルでスクロール量を扱う画面向け。
 * コンテンツがビューポートに収まる場合はスクロールバー自体が不要なので null を返す。
 */
internal fun pixelScrollbarMetrics(
    scrollOffsetPx: Float,
    contentHeightPx: Float,
    viewportHeightPx: Float,
): ScrollbarMetrics? {
    if (contentHeightPx <= viewportHeightPx) {
        return null
    }

    val thumbHeightFraction = (viewportHeightPx / contentHeightPx).coerceIn(0.08f, 1f)
    val maxScrollOffsetPx = contentHeightPx - viewportHeightPx
    val positionFraction = (scrollOffsetPx / maxScrollOffsetPx).coerceIn(0f, 1f)

    return ScrollbarMetrics(
        positionFraction = positionFraction,
        thumbHeightFraction = thumbHeightFraction,
    )
}

/**
 * ドラッグ位置（0f..1f）から、実測ピクセルでのスクロールオフセットを算出する。
 * [pixelScrollbarMetrics] の positionFraction と往復整合する。
 */
internal fun pixelScrollbarTargetOffset(
    positionFraction: Float,
    contentHeightPx: Float,
    viewportHeightPx: Float,
): Float {
    val maxScrollOffsetPx = (contentHeightPx - viewportHeightPx).coerceAtLeast(0f)
    return positionFraction.coerceIn(0f, 1f) * maxScrollOffsetPx
}
