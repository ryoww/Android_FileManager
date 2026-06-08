package com.ryo.androidfilemanager.data.smb

interface RemoteReadableFile {
    val size: Long

    suspend fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int

    suspend fun close()
}

