package com.ryo.androidfilemanager.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSelectionTest {
    @Test
    fun initiallyInactiveAndEmpty() {
        val selection = FileSelection()

        assertFalse(selection.isActive)
        assertEquals(0, selection.count)
    }

    @Test
    fun toggleAddsThenRemovesPath() {
        val added = FileSelection().toggle("/a")
        assertTrue("/a" in added)
        assertTrue(added.isActive)
        assertEquals(1, added.count)

        val removed = added.toggle("/a")
        assertFalse("/a" in removed)
        assertFalse(removed.isActive)
    }

    @Test
    fun clearEmptiesSelection() {
        val selection = FileSelection().toggle("/a").toggle("/b").clear()

        assertFalse(selection.isActive)
        assertEquals(0, selection.count)
    }

    @Test
    fun selectedFromKeepsEntriesOrderAndFiltersUnselected() {
        val entries = listOf(
            file("/a"),
            file("/b"),
            file("/c"),
        )
        val selection = FileSelection().toggle("/c").toggle("/a")

        val result = selection.selectedFrom(entries)

        assertEquals(listOf("/a", "/c"), result.map { it.path })
    }

    private fun file(path: String): FileItem = FileItem(
        name = path.substringAfterLast("/"),
        path = path,
        uri = null,
        isDirectory = false,
        size = null,
        modifiedAt = null,
        mimeType = null,
        sourceType = SourceType.LOCAL,
    )
}
