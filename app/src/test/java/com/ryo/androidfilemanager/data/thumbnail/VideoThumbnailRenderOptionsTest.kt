package com.ryo.androidfilemanager.data.thumbnail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoThumbnailRenderOptionsTest {

    @Test
    fun `quality preset tries representative frame candidates`() {
        assertEquals(
            listOf(0L, 1_000_000L, 3_000_000L),
            videoThumbnailCandidateTimesUs(
                durationUs = 10_000_000L,
                frameSelection = VideoThumbnailFrameSelection.Representative,
            ),
        )
    }

    @Test
    fun `unknown duration uses first frame only`() {
        assertEquals(
            listOf(0L),
            videoThumbnailCandidateTimesUs(
                durationUs = 0L,
                frameSelection = VideoThumbnailFrameSelection.Representative,
            ),
        )
    }

    @Test
    fun `smb fast preset retries ten percent only when first frame is dark`() {
        assertEquals(
            VideoThumbnailFrameSelection.FirstFrameThenTenPercentIfDark,
            VideoThumbnailRenderPresets.SmbFast.frameSelection,
        )
        assertFalse(VideoThumbnailRenderPresets.SmbFast.allowCodecFallback)
        assertEquals(
            listOf(0L, 1_000_000L),
            videoThumbnailCandidateTimesUs(
                durationUs = 10_000_000L,
                frameSelection = VideoThumbnailRenderPresets.SmbFast.frameSelection,
            ),
        )
    }

    @Test
    fun `quality preset keeps codec fallback enabled`() {
        assertEquals(
            VideoThumbnailFrameSelection.Representative,
            VideoThumbnailRenderPresets.Quality.frameSelection,
        )
        assertTrue(VideoThumbnailRenderPresets.Quality.allowCodecFallback)
    }
}
