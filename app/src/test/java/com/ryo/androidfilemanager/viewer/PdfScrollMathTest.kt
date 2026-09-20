package com.ryo.androidfilemanager.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfScrollMathTest {

    @Test
    fun estimateListScroll_atFirstPageTop_offsetIsZeroAndContentHeightSumsAllPages() {
        val result = estimateListScroll(
            firstVisibleIndex = 0,
            firstVisibleScrollOffsetPx = 0,
            averageItemHeightPx = 1000f,
            itemSpacingPx = 20f,
            itemCount = 10,
            contentPaddingPx = 8f,
        )

        assertEquals(0f, result.offsetPx, 0.0001f)
        // 1000*10 + 20*9 + 8*2
        assertEquals(1000f * 10 + 20f * 9 + 8f * 2, result.contentHeightPx, 0.0001f)
    }

    @Test
    fun estimateListScroll_partwayThroughThirdPage_offsetAccountsForPrecedingPagesAndSpacing() {
        val result = estimateListScroll(
            firstVisibleIndex = 2,
            firstVisibleScrollOffsetPx = 300,
            averageItemHeightPx = 1000f,
            itemSpacingPx = 20f,
            itemCount = 10,
            contentPaddingPx = 8f,
        )

        assertEquals(2 * (1000f + 20f) + 300f, result.offsetPx, 0.0001f)
    }

    @Test
    fun listScrollTargetForOffset_roundTripsWithEstimateListScroll() {
        val averageItemHeightPx = 1000f
        val itemSpacingPx = 20f
        val itemCount = 10

        val estimated = estimateListScroll(
            firstVisibleIndex = 2,
            firstVisibleScrollOffsetPx = 300,
            averageItemHeightPx = averageItemHeightPx,
            itemSpacingPx = itemSpacingPx,
            itemCount = itemCount,
            contentPaddingPx = 8f,
        )

        val target = listScrollTargetForOffset(
            offsetPx = estimated.offsetPx,
            averageItemHeightPx = averageItemHeightPx,
            itemSpacingPx = itemSpacingPx,
            itemCount = itemCount,
        )

        assertEquals(2, target.index)
        assertEquals(300, target.scrollOffsetPx)
    }

    @Test
    fun listScrollTargetForOffset_beyondContentEnd_clampsToLastPage() {
        val target = listScrollTargetForOffset(
            offsetPx = 1_000_000f,
            averageItemHeightPx = 1000f,
            itemSpacingPx = 20f,
            itemCount = 10,
        )

        assertEquals(9, target.index)
    }

    @Test
    fun listScrollTargetForOffset_negativeOffset_clampsToFirstPageStart() {
        val target = listScrollTargetForOffset(
            offsetPx = -500f,
            averageItemHeightPx = 1000f,
            itemSpacingPx = 20f,
            itemCount = 10,
        )

        assertEquals(0, target.index)
        assertEquals(0, target.scrollOffsetPx)
    }

    @Test
    fun estimateListScroll_whenNoPagesOrZeroHeight_returnsZeroWithoutThrowing() {
        val noPages = estimateListScroll(
            firstVisibleIndex = 0,
            firstVisibleScrollOffsetPx = 0,
            averageItemHeightPx = 1000f,
            itemSpacingPx = 20f,
            itemCount = 0,
            contentPaddingPx = 8f,
        )
        assertEquals(0f, noPages.offsetPx, 0.0001f)
        assertEquals(0f, noPages.contentHeightPx, 0.0001f)

        val zeroHeight = estimateListScroll(
            firstVisibleIndex = 0,
            firstVisibleScrollOffsetPx = 0,
            averageItemHeightPx = 0f,
            itemSpacingPx = 20f,
            itemCount = 10,
            contentPaddingPx = 8f,
        )
        assertEquals(0f, zeroHeight.offsetPx, 0.0001f)
        assertEquals(0f, zeroHeight.contentHeightPx, 0.0001f)
    }

    @Test
    fun listScrollTargetForOffset_whenNoPagesOrZeroHeight_returnsZeroWithoutThrowing() {
        val noPages = listScrollTargetForOffset(
            offsetPx = 500f,
            averageItemHeightPx = 1000f,
            itemSpacingPx = 20f,
            itemCount = 0,
        )
        assertEquals(0, noPages.index)
        assertEquals(0, noPages.scrollOffsetPx)

        val zeroHeight = listScrollTargetForOffset(
            offsetPx = 500f,
            averageItemHeightPx = 0f,
            itemSpacingPx = 20f,
            itemCount = 10,
        )
        assertEquals(0, zeroHeight.index)
        assertEquals(0, zeroHeight.scrollOffsetPx)
    }
}
