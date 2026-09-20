package com.ryo.androidfilemanager.core.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileFilterTest {
    @Test
    fun directoryOnlyMatchesAllFilter() {
        val directory = item(name = "folder", isDirectory = true, mimeType = null)

        assertTrue(directory.matchesFilter(FileFilter.ALL))
        assertFalse(directory.matchesFilter(FileFilter.PDF))
        assertFalse(directory.matchesFilter(FileFilter.VIDEO))
        assertFalse(directory.matchesFilter(FileFilter.CODE))
        assertFalse(directory.matchesFilter(FileFilter.IMAGES))
    }

    @Test
    fun pdfFileMatchesPdfFilterOnly() {
        val pdf = item(name = "report.pdf", mimeType = null)

        assertTrue(pdf.matchesFilter(FileFilter.PDF))
        assertFalse(pdf.matchesFilter(FileFilter.IMAGES))
    }

    @Test
    fun imageFileMatchesImagesFilter() {
        val image = item(name = "photo.jpg", mimeType = "image/jpeg")

        assertTrue(image.matchesFilter(FileFilter.IMAGES))
    }

    @Test
    fun videoFileMatchesVideoFilter() {
        val video = item(name = "clip.mp4", mimeType = "video/mp4")

        assertTrue(video.matchesFilter(FileFilter.VIDEO))
    }

    @Test
    fun codeFileMatchesCodeFilter() {
        val code = item(name = "Main.kt", mimeType = null)

        assertTrue(code.matchesFilter(FileFilter.CODE))
    }

    @Test
    fun unrecognizedFileMatchesOnlyAllFilter() {
        val plain = item(name = "notes.txt", mimeType = null)

        assertTrue(plain.matchesFilter(FileFilter.ALL))
        assertFalse(plain.matchesFilter(FileFilter.PDF))
        assertFalse(plain.matchesFilter(FileFilter.VIDEO))
        assertFalse(plain.matchesFilter(FileFilter.CODE))
        assertFalse(plain.matchesFilter(FileFilter.IMAGES))
    }

    private fun item(
        name: String,
        isDirectory: Boolean = false,
        mimeType: String?,
    ): FileItem = FileItem(
        name = name,
        path = "/root/$name",
        uri = null,
        isDirectory = isDirectory,
        size = null,
        modifiedAt = null,
        mimeType = mimeType,
        sourceType = SourceType.LOCAL,
    )
}
