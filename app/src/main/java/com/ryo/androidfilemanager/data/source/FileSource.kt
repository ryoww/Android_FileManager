package com.ryo.androidfilemanager.data.source

import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.OpenedFile
import com.ryo.androidfilemanager.data.model.TransferProgress

interface FileSource {
    suspend fun list(path: String): List<FileItem>
    suspend fun open(
        file: FileItem,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): OpenedFile
}

