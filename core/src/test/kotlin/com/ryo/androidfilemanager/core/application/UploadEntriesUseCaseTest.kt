package com.ryo.androidfilemanager.core.application

import com.ryo.androidfilemanager.core.domain.DirectoryNavigation
import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.TransferProgress
import com.ryo.androidfilemanager.core.domain.TransferSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class RecordingUploadTransfer(
    private val uploadSummary: TransferSummary = TransferSummary(0, ""),
) : com.ryo.androidfilemanager.core.application.port.FileTransfer {
    var uploadedSources: List<String>? = null
        private set
    var uploadedDestination: String? = null
        private set

    override suspend fun download(
        files: List<FileItem>,
        onProgress: ((TransferProgress) -> Unit)?,
    ): TransferSummary = error("not used")

    override suspend fun upload(
        sources: List<String>,
        remoteDirectoryPath: String,
        onProgress: ((TransferProgress) -> Unit)?,
    ): TransferSummary {
        uploadedSources = sources
        uploadedDestination = remoteDirectoryPath
        return uploadSummary
    }
}

class UploadEntriesUseCaseTest {
    @Test
    fun 送信元が空のときは転送せず例外を投げる() = runTest {
        val transfer = RecordingUploadTransfer()
        val useCase = UploadEntriesUseCase(transfer)

        try {
            useCase(emptyList(), DirectoryNavigation.root("/current"))
            org.junit.Assert.fail("expected NoEntriesSelectedException")
        } catch (expected: NoEntriesSelectedException) {
            // ok
        }

        assertEquals(null, transfer.uploadedSources)
    }

    @Test
    fun 現在のフォルダをアップロード先にして転送する() = runTest {
        val summary = TransferSummary(fileCount = 1, destinationPath = "/current")
        val transfer = RecordingUploadTransfer(uploadSummary = summary)
        val useCase = UploadEntriesUseCase(transfer)
        val sources = listOf("content://a")

        val result = useCase(sources, DirectoryNavigation.root("/current"))

        assertEquals(sources, transfer.uploadedSources)
        assertEquals("/current", transfer.uploadedDestination)
        assertEquals(summary, result)
    }

    @Test
    fun ルートではアップロード先を空文字にする() = runTest {
        val transfer = RecordingUploadTransfer()
        val useCase = UploadEntriesUseCase(transfer)
        val sources = listOf("content://a")

        useCase(sources, DirectoryNavigation.Empty)

        assertEquals("", transfer.uploadedDestination)
    }
}
