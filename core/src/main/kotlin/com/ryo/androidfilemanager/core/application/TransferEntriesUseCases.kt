package com.ryo.androidfilemanager.core.application

import com.ryo.androidfilemanager.core.application.port.FileTransfer
import com.ryo.androidfilemanager.core.domain.DirectoryNavigation
import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.FileSelection
import com.ryo.androidfilemanager.core.domain.TransferProgress
import com.ryo.androidfilemanager.core.domain.TransferSummary

class NoEntriesSelectedException : IllegalArgumentException("No entries selected.")

class DownloadDestinationUnavailableException : IllegalStateException("Download destination is not writable.")

// Why not Result: 呼び出し側（ViewModel）が既に runCatching で例外を扱っており、
// 検証失敗と転送失敗の扱いを分けないため
class DownloadEntriesUseCase(
    private val transfer: FileTransfer,
    private val canWriteDownloads: () -> Boolean,
) {
    suspend operator fun invoke(
        selection: FileSelection,
        entries: List<FileItem>,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): TransferSummary {
        val selectedFiles = selection.selectedFrom(entries)
        if (selectedFiles.isEmpty()) {
            throw NoEntriesSelectedException()
        }
        if (!canWriteDownloads()) {
            throw DownloadDestinationUnavailableException()
        }
        return transfer.download(selectedFiles, onProgress)
    }
}

class UploadEntriesUseCase(
    private val transfer: FileTransfer,
) {
    suspend operator fun invoke(
        sources: List<String>,
        navigation: DirectoryNavigation,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): TransferSummary {
        if (sources.isEmpty()) {
            throw NoEntriesSelectedException()
        }
        return transfer.upload(sources, navigation.currentPath ?: "", onProgress)
    }
}
