package com.ryo.androidfilemanager.data.smb

class SmbReadableFile(
    override val size: Long,
    private val readBlock: suspend (position: Long, buffer: ByteArray, offset: Int, length: Int) -> Int,
    private val closeBlock: suspend () -> Unit,
) : RemoteReadableFile {
    override suspend fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = readBlock(position, buffer, offset, length)

    override suspend fun close() = closeBlock()
}

