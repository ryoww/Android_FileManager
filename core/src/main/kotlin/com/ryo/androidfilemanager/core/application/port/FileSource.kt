package com.ryo.androidfilemanager.core.application.port

import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.OpenedFile
import com.ryo.androidfilemanager.core.domain.TransferProgress

interface FileSource {
    suspend fun list(path: String): List<FileItem>
    suspend fun open(
        file: FileItem,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): OpenedFile
}

