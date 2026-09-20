package com.ryo.androidfilemanager.core.application.port

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

