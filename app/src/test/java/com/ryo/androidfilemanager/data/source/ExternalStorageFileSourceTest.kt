package com.ryo.androidfilemanager.data.source

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalStorageFileSourceTest {
    private val root = Files.createTempDirectory("external-source-test").toFile()
    private val source = ExternalStorageFileSource(root)

    @After
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun listSortsDirectoriesBeforeFilesByName() = runBlocking {
        File(root, "z.txt").writeText("z")
        File(root, "Download").mkdirs()
        File(root, "a.txt").writeText("a")

        val items = source.list(source.rootPath)

        assertEquals(listOf("Download", "a.txt", "z.txt"), items.map { it.name })
        assertTrue(items.first().isDirectory)
    }

    @Test
    fun displayNameRecognizesDownloadFolder() {
        val downloads = File(root, "Download").apply { mkdirs() }

        assertEquals("Download", source.displayName(downloads.path))
    }

    @Test
    fun navigateUpLabelFromDownloadPointsToInternalStorage() {
        val downloads = File(root, "Download").apply { mkdirs() }

        assertEquals("Internal Storage", source.navigateUpLabel(listOf(source.rootPath, downloads.path)))
    }
}
