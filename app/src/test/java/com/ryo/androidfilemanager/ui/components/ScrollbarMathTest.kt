package com.ryo.androidfilemanager.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollbarMathTest {

    @Test
    fun targetIndex_atFractionZero_isZero() {
        val index = scrollbarTargetIndex(
            positionFraction = 0f,
            totalItemCount = 100,
            visibleItemCount = 10,
        )

        assertEquals(0, index)
    }

    @Test
    fun targetIndex_atFractionOne_isMaxFirstVisibleIndex() {
        val index = scrollbarTargetIndex(
            positionFraction = 1f,
            totalItemCount = 100,
            visibleItemCount = 10,
        )

        assertEquals(90, index)
    }

    @Test
    fun targetIndex_isMonotonicallyNonDecreasingWithFraction() {
        val totalItemCount = 250
        val visibleItemCount = 15
        val fractions = (0..20).map { it / 20f }

        var previousIndex = -1
        for (fraction in fractions) {
            val index = scrollbarTargetIndex(fraction, totalItemCount, visibleItemCount)
            assertTrue(
                "index should not decrease as fraction increases (fraction=$fraction, index=$index, previous=$previousIndex)",
                index >= previousIndex,
            )
            previousIndex = index
        }
    }

    @Test
    fun targetIndex_whenVisibleCoversAllItems_isAlwaysZero() {
        assertEquals(0, scrollbarTargetIndex(0f, totalItemCount = 10, visibleItemCount = 10))
        assertEquals(0, scrollbarTargetIndex(0.5f, totalItemCount = 10, visibleItemCount = 10))
        assertEquals(0, scrollbarTargetIndex(1f, totalItemCount = 10, visibleItemCount = 10))
        assertEquals(0, scrollbarTargetIndex(1f, totalItemCount = 5, visibleItemCount = 10))
    }

    @Test
    fun targetIndex_clampsOutOfRangeFractions() {
        assertEquals(
            0,
            scrollbarTargetIndex(positionFraction = -1f, totalItemCount = 100, visibleItemCount = 10),
        )
        assertEquals(
            90,
            scrollbarTargetIndex(positionFraction = 2f, totalItemCount = 100, visibleItemCount = 10),
        )
    }

    @Test
    fun metricsAndTargetIndex_roundTripConsistently() {
        val totalItemCount = 300
        val visibleItemCount = 20
        val maxFirstVisibleIndex = totalItemCount - visibleItemCount

        val sampleFirstIndices = listOf(0, 1, 25, 50, 100, 140, maxFirstVisibleIndex)
        for (firstIndex in sampleFirstIndices) {
            val metrics = scrollbarMetrics(
                firstVisibleIndex = firstIndex,
                visibleItemCount = visibleItemCount,
                totalItemCount = totalItemCount,
            )

            val roundTrippedIndex = scrollbarTargetIndex(
                positionFraction = metrics.positionFraction,
                totalItemCount = totalItemCount,
                visibleItemCount = visibleItemCount,
            )

            assertEquals(
                "round trip should return to the original firstIndex=$firstIndex",
                firstIndex,
                roundTrippedIndex,
            )
        }
    }

    @Test
    fun dragFraction_clampsBelowZeroAndAboveOne() {
        // トラック高 200, サム高 50 → 可動域 150
        val trackHeight = 200f
        val thumbHeight = 50f

        // touchY が小さすぎる場合は 0f にクランプ
        assertEquals(
            0f,
            scrollbarDragFraction(touchY = 0f, trackHeight = trackHeight, thumbHeight = thumbHeight),
        )

        // touchY が大きすぎる場合は 1f にクランプ
        assertEquals(
            1f,
            scrollbarDragFraction(touchY = 500f, trackHeight = trackHeight, thumbHeight = thumbHeight),
        )
    }

    @Test
    fun dragFraction_atMidpoint_isHalf() {
        // トラック高 200, サム高 0 とすると travel = 200
        // touchY = 100 (中央) -> fraction = (100 - 0) / 200 = 0.5
        val fraction = scrollbarDragFraction(touchY = 100f, trackHeight = 200f, thumbHeight = 0f)

        assertEquals(0.5f, fraction, 0.0001f)
    }

    @Test
    fun dragFraction_whenTrackHeightNotGreaterThanThumbHeight_isZero() {
        assertEquals(
            0f,
            scrollbarDragFraction(touchY = 50f, trackHeight = 100f, thumbHeight = 100f),
        )
        assertEquals(
            0f,
            scrollbarDragFraction(touchY = 50f, trackHeight = 100f, thumbHeight = 150f),
        )
    }

    @Test
    fun pixelMetrics_whenContentFitsInViewport_returnsNull() {
        assertEquals(
            null,
            pixelScrollbarMetrics(scrollOffsetPx = 0f, contentHeightPx = 1000f, viewportHeightPx = 2000f),
        )
        // ちょうど収まる場合（等しい）もスクロールバー不要
        assertEquals(
            null,
            pixelScrollbarMetrics(scrollOffsetPx = 0f, contentHeightPx = 1000f, viewportHeightPx = 1000f),
        )
    }

    @Test
    fun pixelMetrics_atTopAndBottom_hasPositionZeroAndOne() {
        val contentHeightPx = 5000f
        val viewportHeightPx = 1000f

        val atTop = pixelScrollbarMetrics(
            scrollOffsetPx = 0f,
            contentHeightPx = contentHeightPx,
            viewportHeightPx = viewportHeightPx,
        )
        assertEquals(0f, atTop!!.positionFraction, 0.0001f)

        val atBottom = pixelScrollbarMetrics(
            scrollOffsetPx = contentHeightPx - viewportHeightPx,
            contentHeightPx = contentHeightPx,
            viewportHeightPx = viewportHeightPx,
        )
        assertEquals(1f, atBottom!!.positionFraction, 0.0001f)
    }

    @Test
    fun pixelMetrics_thumbHeightFraction_isClampedToMinimum() {
        val metrics = pixelScrollbarMetrics(
            scrollOffsetPx = 0f,
            contentHeightPx = 100000f,
            viewportHeightPx = 1000f,
        )

        assertEquals(0.08f, metrics!!.thumbHeightFraction, 0.0001f)
    }

    @Test
    fun pixelTargetOffset_isProportionalToFractionAndClampsOutOfRangeFractions() {
        val contentHeightPx = 5000f
        val viewportHeightPx = 1000f
        val maxOffsetPx = contentHeightPx - viewportHeightPx

        assertEquals(
            0f,
            pixelScrollbarTargetOffset(-1f, contentHeightPx, viewportHeightPx),
            0.0001f,
        )
        assertEquals(
            maxOffsetPx,
            pixelScrollbarTargetOffset(2f, contentHeightPx, viewportHeightPx),
            0.0001f,
        )
        assertEquals(
            maxOffsetPx / 2f,
            pixelScrollbarTargetOffset(0.5f, contentHeightPx, viewportHeightPx),
            0.0001f,
        )
    }

    @Test
    fun thumbBounds_belowMinimumHeight_isLiftedToMinThumbHeight() {
        // trackHeight 1000, thumbHeightFraction 0.01 (=10px) は minThumbHeightPx=28 を下回るため
        // 実際のサム高さは 28px に持ち上がる
        val bounds = scrollbarThumbBounds(
            trackHeightPx = 1000f,
            metrics = ScrollbarMetrics(positionFraction = 0f, thumbHeightFraction = 0.01f),
            minThumbHeightPx = 28f,
        )

        assertEquals(28f, bounds.endInclusive - bounds.start, 0.0001f)
    }

    @Test
    fun thumbBounds_atPositionOne_bottomReachesTrackHeight() {
        val trackHeightPx = 1000f
        val bounds = scrollbarThumbBounds(
            trackHeightPx = trackHeightPx,
            metrics = ScrollbarMetrics(positionFraction = 1f, thumbHeightFraction = 0.2f),
            minThumbHeightPx = 28f,
        )

        assertEquals(trackHeightPx, bounds.endInclusive, 0.0001f)
    }

    @Test
    fun pixelMetricsAndTargetOffset_roundTripConsistently() {
        val contentHeightPx = 8000f
        val viewportHeightPx = 1200f
        val offsetPx = 1234f

        val metrics = pixelScrollbarMetrics(offsetPx, contentHeightPx, viewportHeightPx)
        val roundTrippedOffsetPx = pixelScrollbarTargetOffset(
            metrics!!.positionFraction,
            contentHeightPx,
            viewportHeightPx,
        )

        assertEquals(offsetPx, roundTrippedOffsetPx, 0.5f)
    }
}
