package com.ryo.androidfilemanager.data.source

import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.OpenedFile

interface FileSource {
    suspend fun list(path: String): List<FileItem>
    suspend fun open(file: FileItem): OpenedFile
}

