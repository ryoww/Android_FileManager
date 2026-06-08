package com.ryo.androidfilemanager.data.model

data class FileItem(
    val name: String,
    val path: String,
    val uri: String?,
    val isDirectory: Boolean,
    val size: Long?,
    val modifiedAt: Long?,
    val mimeType: String?,
    val sourceType: SourceType,
)

