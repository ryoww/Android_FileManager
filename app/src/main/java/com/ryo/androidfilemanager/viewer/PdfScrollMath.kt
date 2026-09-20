package com.ryo.androidfilemanager.viewer

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 推定スクロールオフセットとコンテンツ高さ（いずれも px）。
 * Android 依存を持たないため JVM ユニットテストが可能。
 */
internal data class EstimatedListScroll(
    val offsetPx: Float,
    val contentHeightPx: Float,
)

/**
 * `LazyListState` の先頭可視アイテム情報から、リスト全体のスクロールオフセットと
 * コンテンツ高さを推定する。
 *
 * Why not: 全ページの高さが均一と仮定した推定になっている。PDF は用紙サイズが混在する
 * ことがあり、正確な累積高さを出すには全ページ分の実測が要るが、それではスクロールバー
 * 表示のためだけに全ページをレンダリング・計測することになりコストが見合わない。多少
 * ずれてもスクロールバーの目安としては十分なため、平均高さでの近似を採る。
 */
internal fun estimateListScroll(
    firstVisibleIndex: Int,
    firstVisibleScrollOffsetPx: Int,
    averageItemHeightPx: Float,
    itemSpacingPx: Float,
    itemCount: Int,
    contentPaddingPx: Float,
): EstimatedListScroll {
    if (itemCount <= 0 || averageItemHeightPx <= 0f) {
        return EstimatedListScroll(offsetPx = 0f, contentHeightPx = 0f)
    }

    val stride = averageItemHeightPx + itemSpacingPx
    val offsetPx = firstVisibleIndex * stride + firstVisibleScrollOffsetPx
    val contentHeightPx = itemCount * averageItemHeightPx +
        (itemCount - 1).coerceAtLeast(0) * itemSpacingPx +
        contentPaddingPx * 2

    return EstimatedListScroll(offsetPx = offsetPx, contentHeightPx = contentHeightPx)
}

/**
 * `LazyListState.scrollToItem` に渡す先頭アイテム index とその中でのオフセット（px）。
 */
internal data class ListScrollTarget(
    val index: Int,
    val scrollOffsetPx: Int,
)

/**
 * [estimateListScroll] が返す推定オフセットから、`scrollToItem` に渡す index / offset を
 * 逆算する。均一高さの仮定は estimateListScroll と共通（同ファイル内の Why not を参照）。
 */
internal fun listScrollTargetForOffset(
    offsetPx: Float,
    averageItemHeightPx: Float,
    itemSpacingPx: Float,
    itemCount: Int,
): ListScrollTarget {
    if (itemCount <= 0 || averageItemHeightPx <= 0f) {
        return ListScrollTarget(index = 0, scrollOffsetPx = 0)
    }
    if (offsetPx < 0f) {
        return ListScrollTarget(index = 0, scrollOffsetPx = 0)
    }

    val stride = averageItemHeightPx + itemSpacingPx
    val index = floor(offsetPx / stride).toInt().coerceIn(0, itemCount - 1)
    val scrollOffsetPx = (offsetPx - index * stride).roundToInt().coerceAtLeast(0)

    return ListScrollTarget(index = index, scrollOffsetPx = scrollOffsetPx)
}
