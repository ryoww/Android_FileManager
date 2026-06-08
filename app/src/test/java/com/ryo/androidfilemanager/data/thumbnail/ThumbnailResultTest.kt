package com.ryo.androidfilemanager.data.thumbnail

import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.SourceType
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailResultTest {
    @Test
    fun thumbnailKeyIncludesSourceAndPath() {
        val local = file(path = "/local/report.pdf", sourceType = SourceType.LOCAL)
        val smb = file(path = "/local/report.pdf", sourceType = SourceType.SMB)

        assertNotEquals(thumbnailKey(local), thumbnailKey(smb))
    }

    @Test
    fun thumbnailKeyChangesWhenFileChanges() {
        val oldFile = file(modifiedAt = 100L)
        val newFile = file(modifiedAt = 200L)

        assertNotEquals(thumbnailKey(oldFile), thumbnailKey(newFile))
    }

    @Test
    fun rawThumbnailKeyKeepsExpectedFields() {
        val key = thumbnailKey(
            sourceType = "LOCAL",
            pathOrUri = "content://demo/file.pdf",
            size = 128L,
            modifiedAt = 456L,
        )

        assertTrue(key.contains("LOCAL"))
        assertTrue(key.contains("content://demo/file.pdf"))
        assertTrue(key.contains("128"))
        assertTrue(key.contains("456"))
    }

    private fun file(
        path: String = "/local/report.pdf",
        sourceType: SourceType = SourceType.LOCAL,
        modifiedAt: Long = 100L,
    ): FileItem = FileItem(
        name = "report.pdf",
        path = path,
        uri = path,
        isDirectory = false,
        size = 128L,
        modifiedAt = modifiedAt,
        mimeType = "application/pdf",
        sourceType = sourceType,
    )
}

