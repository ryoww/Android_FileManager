package com.ryo.androidfilemanager.core.application

import com.ryo.androidfilemanager.core.application.port.FileSource
import com.ryo.androidfilemanager.core.domain.DirectoryNavigation
import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.OpenedFile
import com.ryo.androidfilemanager.core.domain.SourceType
import com.ryo.androidfilemanager.core.domain.TransferProgress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeFileSource(
    private val entriesByPath: Map<String, List<FileItem>>,
    private val failingPaths: Set<String> = emptySet(),
) : FileSource {
    var listCallCount = 0
        private set

    override suspend fun list(path: String): List<FileItem> {
        listCallCount++
        if (path in failingPaths) error("boom: $path")
        return entriesByPath[path] ?: error("no entries stubbed for $path")
    }

    override suspend fun open(file: FileItem, onProgress: ((TransferProgress) -> Unit)?): OpenedFile =
        error("not used")
}

class DirectoryBrowserTest {
    @Test
    fun enterListsNewPathAndDeepensNavigation() = runTest {
        val fileSource = FakeFileSource(
            mapOf("/root/child" to listOf(file("photo.jpg"))),
        )
        val browser = DirectoryBrowser(fileSource)
        val navigation = DirectoryNavigation.root("/root")

        val outcome = browser.enter(navigation, "/root/child")

        assertEquals("/root/child", outcome.navigation.currentPath)
        assertEquals(listOf("photo.jpg"), outcome.entries.map { it.name })
    }

    @Test
    fun upNavigatesBackAndRelistsWithoutCallingFileSourceAtRoot() = runTest {
        val fileSource = FakeFileSource(
            mapOf("/root" to listOf(file("root-file"))),
        )
        val browser = DirectoryBrowser(fileSource)
        val root = DirectoryNavigation.root("/root")

        val atRoot = browser.up(root)
        assertNull(atRoot)
        assertEquals(0, fileSource.listCallCount)

        val child = root.enter("/root/child")
        val fileSourceWithChild = FakeFileSource(
            mapOf("/root" to listOf(file("root-file"))),
        )
        val browserWithChild = DirectoryBrowser(fileSourceWithChild)
        val outcome = browserWithChild.up(child)

        assertEquals("/root", outcome?.navigation?.currentPath)
        assertEquals(listOf("root-file"), outcome?.entries?.map { it.name })
    }

    @Test
    fun reloadRefetchesCurrentPath() = runTest {
        val fileSource = FakeFileSource(
            mapOf("/root" to listOf(file("a"), file("b"))),
        )
        val browser = DirectoryBrowser(fileSource)
        val navigation = DirectoryNavigation.root("/root")

        val outcome = browser.reload(navigation)

        assertEquals("/root", outcome.navigation.currentPath)
        assertEquals(listOf("a", "b"), outcome.entries.map { it.name })
    }

    @Test
    fun reloadWithNoCurrentPathThrows() = runTest {
        val browser = DirectoryBrowser(FakeFileSource(emptyMap()))

        val result = runCatching { browser.reload(DirectoryNavigation.Empty) }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun fileSourceFailurePropagates() = runTest {
        val fileSource = FakeFileSource(
            entriesByPath = emptyMap(),
            failingPaths = setOf("/root/broken"),
        )
        val browser = DirectoryBrowser(fileSource)
        val navigation = DirectoryNavigation.root("/root")

        val result = runCatching { browser.enter(navigation, "/root/broken") }

        assertTrue(result.isFailure)
        assertEquals("/root", navigation.currentPath)
    }

    private fun file(name: String): FileItem = FileItem(
        name = name,
        path = "/root/$name",
        uri = null,
        isDirectory = false,
        size = null,
        modifiedAt = null,
        mimeType = null,
        sourceType = SourceType.LOCAL,
    )
}
