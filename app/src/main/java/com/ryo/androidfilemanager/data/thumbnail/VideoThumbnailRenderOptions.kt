package com.ryo.androidfilemanager.data.thumbnail

enum class VideoThumbnailFrameSelection {
    FirstFrameOnly,
    FirstFrameThenTenPercentIfDark,
    Representative,
}

data class VideoThumbnailRenderOptions(
    val frameSelection: VideoThumbnailFrameSelection,
    val allowCodecFallback: Boolean,
)

object VideoThumbnailRenderPresets {
    val Quality = VideoThumbnailRenderOptions(
        frameSelection = VideoThumbnailFrameSelection.Representative,
        allowCodecFallback = true,
    )

    val SmbFast = VideoThumbnailRenderOptions(
        frameSelection = VideoThumbnailFrameSelection.FirstFrameThenTenPercentIfDark,
        allowCodecFallback = false,
    )
}

fun videoThumbnailCandidateTimesUs(
    durationUs: Long,
    frameSelection: VideoThumbnailFrameSelection,
): List<Long> = when (frameSelection) {
    VideoThumbnailFrameSelection.FirstFrameOnly -> listOf(0L)
    VideoThumbnailFrameSelection.FirstFrameThenTenPercentIfDark -> {
        if (durationUs > 0L) {
            listOf(0L, durationUs / 10L)
        } else {
            listOf(0L)
        }
    }
    VideoThumbnailFrameSelection.Representative -> {
        if (durationUs > 0L) {
            listOf(0L, durationUs / 10L, durationUs * 3L / 10L)
        } else {
            listOf(0L)
        }
    }
}
