package com.ryo.androidfilemanager.core.application

import com.ryo.androidfilemanager.core.application.port.FileSource
import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.OpenedFile
import com.ryo.androidfilemanager.core.domain.SourceType
import com.ryo.androidfilemanager.core.domain.TransferKind
import com.ryo.androidfilemanager.core.domain.TransferProgress
import com.ryo.androidfilemanager.core.domain.ViewerType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class OpeningFakeFileSource(
    private val resultName: String? = null,
) : FileSource {
    override suspend fun list(path: String): List<FileItem> = error("not used")

    override suspend fun open(file: FileItem, onProgress: ((TransferProgress) -> Unit)?): OpenedFile {
        onProgress?.invoke(TransferProgress(TransferKind.DOWNLOAD, file.name, 1, 2))
        onProgress?.invoke(TransferProgress(TransferKind.DOWNLOAD, file.name, 2, 2))
        return OpenedFile.Local(uri = "content://${file.path}", viewerType = ViewerType.Text, name = resultName)
    }
}

class OpenEntryUseCaseTest {
    @Test
    fun directoryEntryThrows() = runTest {
        val useCase = OpenEntryUseCase(OpeningFakeFileSource())
        val directory = fileItem("Folder", isDirectory = true)

        val result = runCatching { useCase(directory) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun resultNameFallsBackToEntryNameWhenFileSourceLeavesItNull() = runTest {
        val useCase = OpenEntryUseCase(OpeningFakeFileSource(resultName = null))
        val entry = fileItem("notes.txt")

        val opened = useCase(entry)

        assertEquals("notes.txt", opened.name)
    }

    @Test
    fun resultNameFromFileSourceIsNotOverwritten() = runTest {
        val useCase = OpenEntryUseCase(OpeningFakeFileSource(resultName = "remote-name.txt"))
        val entry = fileItem("notes.txt")

        val opened = useCase(entry)

        assertEquals("remote-name.txt", opened.name)
    }

    @Test
    fun onProgressIsForwardedFromFileSource() = runTest {
        val useCase = OpenEntryUseCase(OpeningFakeFileSource())
        val entry = fileItem("notes.txt")
        val received = mutableListOf<TransferProgress>()

        useCase(entry) { received.add(it) }

        assertEquals(2, received.size)
        assertEquals(1L, received[0].bytesTransferred)
        assertEquals(2L, received[1].bytesTransferred)
    }

    private fun fileItem(name: String, isDirectory: Boolean = false): FileItem = FileItem(
        name = name,
        path = "/root/$name",
        uri = null,
        isDirectory = isDirectory,
        size = null,
        modifiedAt = null,
        mimeType = null,
        sourceType = SourceType.LOCAL,
    )
}
