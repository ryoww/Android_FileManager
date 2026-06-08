package com.ryo.androidfilemanager.data.source

import com.ryo.androidfilemanager.data.model.ViewerType
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerTypeDetectorTest {
    @Test
    fun imageMimeTypeRoutesToImageViewer() {
        assertEquals(ViewerType.Image, detectViewerType("photo.bin", "image/jpeg"))
    }

    @Test
    fun pdfExtensionRoutesToPdfViewer() {
        assertEquals(ViewerType.Pdf, detectViewerType("manual.PDF", null))
    }

    @Test
    fun videoMimeTypeRoutesToVideoViewer() {
        assertEquals(ViewerType.Video, detectViewerType("movie", "video/mp4"))
    }

    @Test
    fun textExtensionRoutesToTextViewer() {
        assertEquals(ViewerType.Text, detectViewerType("notes.md", null))
    }

    @Test
    fun codeExtensionRoutesToCodeViewer() {
        assertEquals(ViewerType.Code, detectViewerType("MainActivity.kt", null))
    }

    @Test
    fun unknownExtensionRoutesToUnsupportedViewer() {
        assertEquals(ViewerType.Unsupported, detectViewerType("archive.zip", "application/zip"))
    }
}

