package com.ryo.androidfilemanager.explorer

import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class FileDisplayFormatTest {
    @Test
    fun byteSizeFormatsBytes() {
        assertEquals("42 B", formatByteSize(42))
    }

    @Test
    fun byteSizeFormatsMegabytes() {
        assertEquals("2.0 MB", formatByteSize(2L * 1024L * 1024L))
    }

    @Test
    fun folderSubtitleDoesNotShowSize() {
        val folder = FileItem(
            name = "Documents",
            path = "content://folder",
            uri = "content://folder",
            isDirectory = true,
            size = null,
            modifiedAt = null,
            mimeType = null,
            sourceType = SourceType.LOCAL,
        )

        assertEquals("Folder", fileSubtitle(folder))
    }
}

