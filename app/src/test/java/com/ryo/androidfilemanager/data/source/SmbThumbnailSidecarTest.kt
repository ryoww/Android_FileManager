package com.ryo.androidfilemanager.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbThumbnailSidecarTest {

    @Test
    fun `動画と同じdirectory配下にthumbnail sidecar pathを作る`() {
        assertEquals(
            ".thumbnails/movie.webm.jpg",
            smbThumbnailSidecarPath("movie.webm", "movie.webm"),
        )
        assertEquals(
            "SD_test/secret/movie/.thumbnails/sample.mp4.jpg",
            smbThumbnailSidecarPath(
                filePath = "SD_test/secret/movie/sample.mp4",
                fileName = "sample.mp4",
            ),
        )
    }

    @Test
    fun `thumbnail directoryはSMB一覧に表示しない`() {
        assertTrue(isSmbThumbnailCacheDirectory(".thumbnails"))
        assertTrue(isSmbThumbnailCacheDirectory(".THUMBNAILS"))
        assertFalse(isSmbThumbnailCacheDirectory("thumbnails"))
    }

    @Test
    fun `動画以上に新しいsidecarだけを有効とする`() {
        assertTrue(isFreshSmbThumbnailSidecar(videoModifiedAt = 1_000L, sidecarModifiedAt = 1_000L))
        assertTrue(isFreshSmbThumbnailSidecar(videoModifiedAt = 1_000L, sidecarModifiedAt = 1_001L))
        assertFalse(isFreshSmbThumbnailSidecar(videoModifiedAt = 1_001L, sidecarModifiedAt = 1_000L))
        assertFalse(isFreshSmbThumbnailSidecar(videoModifiedAt = 1_000L, sidecarModifiedAt = null))
        assertTrue(isFreshSmbThumbnailSidecar(videoModifiedAt = null, sidecarModifiedAt = 1_000L))
    }
}
