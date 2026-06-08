package com.ryo.androidfilemanager.data.cache

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileCacheRepositoryTest {
    private val root = Files.createTempDirectory("cache-test").toFile()

    @After
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun calculateDirectorySizeSumsNestedFiles() {
        File(root, "a.bin").writeBytes(ByteArray(10))
        val nested = File(root, "nested").apply { mkdirs() }
        File(nested, "b.bin").writeBytes(ByteArray(15))

        assertEquals(25L, calculateDirectorySize(root))
    }

    @Test
    fun clearDirectoryRemovesChildrenAndRecreatesDirectory() {
        File(root, "a.bin").writeBytes(ByteArray(10))

        clearDirectory(root)

        assertTrue(root.exists())
        assertTrue(root.isDirectory)
        assertEquals(0L, calculateDirectorySize(root))
    }

    @Test
    fun formatCacheSizeFormatsMegabytes() {
        assertEquals("2.0 MB", formatCacheSize(2L * 1024L * 1024L))
    }
}
