package com.ryo.androidfilemanager.data.smb

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SmbReadableFile(
    override val size: Long,
    private val readBlock: suspend (position: Long, buffer: ByteArray, offset: Int, length: Int) -> Int,
    private val closeBlock: suspend () -> Unit,
) : RemoteReadableFile {
    private val closed = AtomicBoolean(false)
    private val readMutex = Mutex()

    override suspend fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (closed.get()) {
            return -1
        }

        return readMutex.withLock {
            if (closed.get()) {
                -1
            } else {
                readBlock(position, buffer, offset, length)
            }
        }
    }

    override suspend fun close() {
        if (closed.compareAndSet(false, true)) {
            closeBlock()
        }
    }
}
