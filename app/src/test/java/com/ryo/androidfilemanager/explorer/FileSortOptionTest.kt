package com.ryo.androidfilemanager.explorer

import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSortOptionTest {
    @Test
    fun defaultSortIsDate() {
        assertEquals(FileSortOption.DATE, FileSortOption.DEFAULT)
    }

    @Test
    fun nameSortKeepsDirectoriesFirst() {
        val sorted = sampleFiles().sortedForDisplay(FileSortOption.NAME)

        assertEquals(listOf("Folder", "a.txt", "z.txt"), sorted.map { it.name })
    }

    @Test
    fun dateSortUsesNewestFilesFirstAfterDirectories() {
        val sorted = sampleFiles().sortedForDisplay(FileSortOption.DATE)

        assertEquals(listOf("Folder", "z.txt", "a.txt"), sorted.map { it.name })
    }

    @Test
    fun sizeSortUsesLargestFilesFirstAfterDirectories() {
        val sorted = sampleFiles().sortedForDisplay(FileSortOption.SIZE)

        assertEquals(listOf("Folder", "a.txt", "z.txt"), sorted.map { it.name })
    }

    private fun sampleFiles(): List<FileItem> = listOf(
        file("z.txt", isDirectory = false, size = 20, modifiedAt = 30),
        file("Folder", isDirectory = true, size = null, modifiedAt = 10),
        file("a.txt", isDirectory = false, size = 40, modifiedAt = 20),
    )

    private fun file(
        name: String,
        isDirectory: Boolean,
        size: Long?,
        modifiedAt: Long?,
    ): FileItem = FileItem(
        name = name,
        path = "/demo/$name",
        uri = null,
        isDirectory = isDirectory,
        size = size,
        modifiedAt = modifiedAt,
        mimeType = null,
        sourceType = SourceType.LOCAL,
    )
}
