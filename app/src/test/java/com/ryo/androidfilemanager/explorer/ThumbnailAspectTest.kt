package com.ryo.androidfilemanager.explorer

import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.SourceType
import com.ryo.androidfilemanager.data.model.ViewerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbnailAspectTest {

    @Test
    fun `pdf thumbnail uses A4 portrait ratio`() {
        assertEquals(
            210f / 297f,
            thumbnailContentAspectRatio(ViewerType.Pdf) ?: error("PDF ratio missing"),
            0.0001f,
        )
    }

    @Test
    fun `non-pdf thumbnails keep existing fill behavior`() {
        assertNull(thumbnailContentAspectRatio(ViewerType.Image))
        assertNull(thumbnailContentAspectRatio(ViewerType.Video))
        assertNull(thumbnailContentAspectRatio(ViewerType.Unsupported))
    }

    @Test
    fun `directory named pdf does not use PDF aspect ratio`() {
        val directory = FileItem(
            name = "pdf",
            path = "/storage/emulated/0/pdf",
            uri = null,
            isDirectory = true,
            size = null,
            modifiedAt = 0L,
            mimeType = null,
            sourceType = SourceType.LOCAL,
        )

        assertNull(thumbnailContentAspectRatio(directory))
    }
}
