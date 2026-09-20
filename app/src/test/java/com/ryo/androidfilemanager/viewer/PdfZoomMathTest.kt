package com.ryo.androidfilemanager.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfZoomMathTest {
    @Test
    fun clampPdfScaleClampsBelowMinimum() {
        assertEquals(PDF_MIN_SCALE, clampPdfScale(0.2f))
    }

    @Test
    fun clampPdfScaleClampsAboveMaximum() {
        assertEquals(PDF_MAX_SCALE, clampPdfScale(10f))
    }

    @Test
    fun clampPdfScaleKeepsValueWithinRange() {
        assertEquals(2.5f, clampPdfScale(2.5f))
    }

    @Test
    fun renderWidthIsAtLeastViewportWidthAtScaleOne() {
        val viewportWidthPx = 1000
        assertTrue(pdfRenderWidthPx(viewportWidthPx, PDF_MIN_SCALE) >= viewportWidthPx)
    }

    @Test
    fun renderWidthIsMultipleOfStep() {
        val renderWidthPx = pdfRenderWidthPx(1000, 1.7f)
        assertEquals(0, renderWidthPx % PDF_RENDER_WIDTH_STEP_PX)
    }

    @Test
    fun renderWidthIsMonotonicNonDecreasingWithScale() {
        val viewportWidthPx = 800
        var previousWidthPx = 0
        var scale = PDF_MIN_SCALE
        while (scale <= PDF_MAX_SCALE) {
            val widthPx = pdfRenderWidthPx(viewportWidthPx, scale)
            assertTrue(widthPx >= previousWidthPx)
            previousWidthPx = widthPx
            scale += 0.25f
        }
    }

    @Test
    fun renderWidthNeverExceedsMax() {
        assertTrue(pdfRenderWidthPx(2000, PDF_MAX_SCALE) <= PDF_MAX_RENDER_WIDTH_PX)
    }

    @Test
    fun currentPageIndexStaysAtFirstVisibleWhenOffsetIsZero() {
        assertEquals(
            2,
            pdfCurrentPageIndex(
                firstVisibleIndex = 2,
                firstVisibleScrollOffsetPx = 0,
                firstVisibleItemHeightPx = 1000,
                pageCount = 10,
            ),
        )
    }

    @Test
    fun currentPageIndexAdvancesWhenOffsetPastHalfway() {
        assertEquals(
            3,
            pdfCurrentPageIndex(
                firstVisibleIndex = 2,
                firstVisibleScrollOffsetPx = 600,
                firstVisibleItemHeightPx = 1000,
                pageCount = 10,
            ),
        )
    }

    @Test
    fun currentPageIndexNeverExceedsLastPage() {
        assertEquals(
            9,
            pdfCurrentPageIndex(
                firstVisibleIndex = 9,
                firstVisibleScrollOffsetPx = 999,
                firstVisibleItemHeightPx = 1000,
                pageCount = 10,
            ),
        )
    }
}
