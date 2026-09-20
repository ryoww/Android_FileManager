package com.ryo.androidfilemanager.core.application

import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.FileSelection
import com.ryo.androidfilemanager.core.domain.SourceType
import com.ryo.androidfilemanager.core.domain.TransferKind
import com.ryo.androidfilemanager.core.domain.TransferProgress
import com.ryo.androidfilemanager.core.domain.TransferSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingFileTransfer(
    private val downloadSummary: TransferSummary = TransferSummary(0, ""),
    private val uploadSummary: TransferSummary = TransferSummary(0, ""),
) : com.ryo.androidfilemanager.core.application.port.FileTransfer {
    var downloadedFiles: List<FileItem>? = null
        private set
    var uploadedSources: List<String>? = null
        private set
    var uploadedDestination: String? = null
        private set

    override suspend fun download(
        files: List<FileItem>,
        onProgress: ((TransferProgress) -> Unit)?,
    ): TransferSummary {
        downloadedFiles = files
        onProgress?.invoke(
            TransferProgress(
                kind = TransferKind.DOWNLOAD,
                fileName = files.firstOrNull()?.name.orEmpty(),
                bytesTransferred = 1L,
                totalBytes = 1L,
            ),
        )
        return downloadSummary
    }

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

private fun file(path: String) = FileItem(
    name = path.substringAfterLast('/'),
    path = path,
    uri = null,
    isDirectory = false,
    size = 10L,
    modifiedAt = null,
    mimeType = null,
    sourceType = SourceType.SMB,
)

class DownloadEntriesUseCaseTest {
    @Test
    fun 未選択のときは転送も権限確認もせず例外を投げる() = runTest {
        val transfer = RecordingFileTransfer()
        var permissionChecked = false
        val useCase = DownloadEntriesUseCase(transfer) { permissionChecked = true; true }

        try {
            useCase(FileSelection(), listOf(file("/a")))
            org.junit.Assert.fail("expected NoEntriesSelectedException")
        } catch (expected: NoEntriesSelectedException) {
            // ok
        }

        assertFalse(permissionChecked)
        assertEquals(null, transfer.downloadedFiles)
    }

    @Test
    fun ダウンロード先へ書き込めないときは転送せず例外を投げる() = runTest {
        val transfer = RecordingFileTransfer()
        val useCase = DownloadEntriesUseCase(transfer) { false }
        val selection = FileSelection(setOf("/a"))

        try {
            useCase(selection, listOf(file("/a")))
            org.junit.Assert.fail("expected DownloadDestinationUnavailableException")
        } catch (expected: DownloadDestinationUnavailableException) {
            // ok
        }

        assertEquals(null, transfer.downloadedFiles)
    }

    @Test
    fun 選択した項目だけを一覧の順序で転送し概要を返す() = runTest {
        val summary = TransferSummary(fileCount = 2, destinationPath = "/Download")
        val transfer = RecordingFileTransfer(downloadSummary = summary)
        val useCase = DownloadEntriesUseCase(transfer) { true }
        val entries = listOf(file("/a"), file("/b"), file("/c"))
        val selection = FileSelection(setOf("/c", "/a"))

        val result = useCase(selection, entries)

        assertEquals(listOf("/a", "/c"), transfer.downloadedFiles?.map { it.path })
        assertEquals(summary, result)
    }

    @Test
    fun 進捗コールバックが転送実装から呼び出し側へそのまま届く() = runTest {
        val transfer = RecordingFileTransfer()
        val useCase = DownloadEntriesUseCase(transfer) { true }
        val entries = listOf(file("/a"))
        val selection = FileSelection(setOf("/a"))
        val received = mutableListOf<TransferProgress>()

        useCase(selection, entries, onProgress = { progress -> received.add(progress) })

        assertTrue(received.isNotEmpty())
    }

    @Test
    fun 検証を通過すると転送前にonStartedが実際の件数で呼ばれる() = runTest {
        val transfer = RecordingFileTransfer()
        val useCase = DownloadEntriesUseCase(transfer) { true }
        val entries = listOf(file("/a"), file("/b"), file("/c"))
        // 選択は3件指定するが、一覧に存在するのは2件だけ → 実際に転送されるのは2件
        val selection = FileSelection(setOf("/a", "/c", "/missing"))
        var startedCount: Int? = null

        useCase(selection, entries, onStarted = { count -> startedCount = count })

        assertEquals(2, startedCount)
        assertEquals(listOf("/a", "/c"), transfer.downloadedFiles?.map { it.path })
    }

    @Test
    fun 未選択またはダウンロード先へ書き込めないときはonStartedが呼ばれない() = runTest {
        val transferForUnselected = RecordingFileTransfer()
        val useCaseForUnselected = DownloadEntriesUseCase(transferForUnselected) { true }
        var unselectedStarted = false

        try {
            useCaseForUnselected(
                FileSelection(),
                listOf(file("/a")),
                onStarted = { unselectedStarted = true },
            )
            org.junit.Assert.fail("expected NoEntriesSelectedException")
        } catch (expected: NoEntriesSelectedException) {
            // ok
        }
        assertFalse(unselectedStarted)

        val transferForUnwritable = RecordingFileTransfer()
        val useCaseForUnwritable = DownloadEntriesUseCase(transferForUnwritable) { false }
        var unwritableStarted = false

        try {
            useCaseForUnwritable(
                FileSelection(setOf("/a")),
                listOf(file("/a")),
                onStarted = { unwritableStarted = true },
            )
            org.junit.Assert.fail("expected DownloadDestinationUnavailableException")
        } catch (expected: DownloadDestinationUnavailableException) {
            // ok
        }
        assertFalse(unwritableStarted)
    }
}
