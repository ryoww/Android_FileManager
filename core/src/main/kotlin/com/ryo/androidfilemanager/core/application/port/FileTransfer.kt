package com.ryo.androidfilemanager.core.application.port

import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.TransferProgress
import com.ryo.androidfilemanager.core.domain.TransferSummary

interface FileTransfer {
    suspend fun download(
        files: List<FileItem>,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): TransferSummary

    /** sources はアダプタが解釈する URI 文字列（content:// など）。core に Android の Uri を持ち込まないため文字列 */
    suspend fun upload(
        sources: List<String>,
        remoteDirectoryPath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): TransferSummary
}
