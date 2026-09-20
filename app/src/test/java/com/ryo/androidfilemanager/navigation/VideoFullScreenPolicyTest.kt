package com.ryo.androidfilemanager.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFullScreenPolicyTest {

    @Test
    fun `orientation change is a no-op for non-video viewers`() {
        val decision = resolveVideoFullScreenOnOrientationChange(
            isVideo = false,
            isLandscape = true,
            currentFullScreen = false,
            suppressAutoFullScreen = false,
        )

        assertEquals(VideoFullScreenDecision(fullScreen = false, suppressAutoFullScreen = false), decision)
    }

    @Test
    fun `rotating to landscape enters full screen automatically for video`() {
        val decision = resolveVideoFullScreenOnOrientationChange(
            isVideo = true,
            isLandscape = true,
            currentFullScreen = false,
            suppressAutoFullScreen = false,
        )

        assertEquals(VideoFullScreenDecision(fullScreen = true, suppressAutoFullScreen = false), decision)
    }

    @Test
    fun `suppress flag keeps landscape video out of full screen`() {
        val decision = resolveVideoFullScreenOnOrientationChange(
            isVideo = true,
            isLandscape = true,
            currentFullScreen = false,
            suppressAutoFullScreen = true,
        )

        assertEquals(VideoFullScreenDecision(fullScreen = false, suppressAutoFullScreen = true), decision)
    }

    @Test
    fun `rotating back to portrait exits full screen and clears the suppress flag`() {
        val decision = resolveVideoFullScreenOnOrientationChange(
            isVideo = true,
            isLandscape = false,
            currentFullScreen = true,
            suppressAutoFullScreen = true,
        )

        assertEquals(VideoFullScreenDecision(fullScreen = false, suppressAutoFullScreen = false), decision)
    }

    @Test
    fun `user exiting full screen in landscape sets the suppress flag`() {
        val decision = resolveVideoFullScreenOnUserExit(isVideo = true, isLandscape = true)

        assertEquals(VideoFullScreenDecision(fullScreen = false, suppressAutoFullScreen = true), decision)
    }

    @Test
    fun `user exiting full screen in portrait does not set the suppress flag`() {
        val decision = resolveVideoFullScreenOnUserExit(isVideo = true, isLandscape = false)

        assertEquals(VideoFullScreenDecision(fullScreen = false, suppressAutoFullScreen = false), decision)
    }
}
