package com.ryo.androidfilemanager.viewer

import kotlin.math.ceil
import kotlin.math.roundToInt

internal const val PDF_MIN_SCALE = 1f
internal const val PDF_MAX_SCALE = 4f
internal const val PDF_MAX_RENDER_WIDTH_PX = 2000
internal const val PDF_RENDER_WIDTH_STEP_PX = 160

internal fun clampPdfScale(scale: Float): Float =
    scale.coerceIn(PDF_MIN_SCALE, PDF_MAX_SCALE)

/**
 * 表示幅 x 倍率を STEP 単位に切り上げて MAX で頭打ちにする。
 * 倍率がわずかに変わるたびに再描画が走らないよう、解像度候補を量子化する。
 */
internal fun pdfRenderWidthPx(viewportWidthPx: Int, scale: Float): Int {
    val targetWidthPx = viewportWidthPx * scale
    val steppedWidthPx = ceil(targetWidthPx / PDF_RENDER_WIDTH_STEP_PX).roundToInt() * PDF_RENDER_WIDTH_STEP_PX
    return steppedWidthPx.coerceAtMost(PDF_MAX_RENDER_WIDTH_PX)
}

/**
 * 可視領域の中央に最も近いページの index。
 * 先頭アイテムのスクロールオフセットが高さの半分を超えていれば次のページを現在ページとみなす。
 */
internal fun pdfCurrentPageIndex(
    firstVisibleIndex: Int,
    firstVisibleScrollOffsetPx: Int,
    firstVisibleItemHeightPx: Int,
    pageCount: Int,
): Int {
    if (pageCount <= 0) return 0
    val isPastHalfway = firstVisibleItemHeightPx > 0 &&
        firstVisibleScrollOffsetPx > firstVisibleItemHeightPx / 2
    val candidateIndex = if (isPastHalfway) firstVisibleIndex + 1 else firstVisibleIndex
    return candidateIndex.coerceIn(0, pageCount - 1)
}
