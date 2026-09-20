package com.ryo.androidfilemanager.data.model

enum class TransferKind { DOWNLOAD, UPLOAD }

data class TransferProgress(
    val kind: TransferKind,
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long?,
    val completedFiles: Int = 0,
    val totalFiles: Int? = null,
) {
    /** 0f..1f。totalBytes が null または 0 以下なら null（UI は不確定バーにする） */
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0L }
            ?.let { (bytesTransferred.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
}
