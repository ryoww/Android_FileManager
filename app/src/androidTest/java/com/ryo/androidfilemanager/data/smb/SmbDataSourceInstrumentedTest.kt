package com.ryo.androidfilemanager.data.smb

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SmbDataSourceInstrumentedTest {
    @Test
    fun readsFromBeginningAndReturnsEndOfInput() {
        val dataSource = SmbDataSource(FakeRemoteReadableFile("abcdef".toByteArray()))
        val length = dataSource.open(DataSpec(Uri.parse("smb://stream/test.mp4")))
        val buffer = ByteArray(8)

        val read = dataSource.read(buffer, 0, buffer.size)
        val eof = dataSource.read(buffer, 0, buffer.size)

        assertEquals(6L, length)
        assertEquals(6, read)
        assertArrayEquals("abcdef".toByteArray(), buffer.copyOf(read))
        assertEquals(C.RESULT_END_OF_INPUT, eof)
        dataSource.close()
    }

    @Test
    fun readsFromSeekPosition() {
        val dataSource = SmbDataSource(FakeRemoteReadableFile("0123456789".toByteArray()))
        val length = dataSource.open(DataSpec(Uri.parse("smb://stream/test.mp4"), 4, 3))
        val buffer = ByteArray(3)

        val read = dataSource.read(buffer, 0, buffer.size)

        assertEquals(3L, length)
        assertEquals(3, read)
        assertArrayEquals("456".toByteArray(), buffer)
        dataSource.close()
    }

    @Test
    fun unknownLengthReadsUntilRemoteEnd() {
        val dataSource = SmbDataSource(
            FakeRemoteReadableFile(
                bytes = "xyz".toByteArray(),
                reportedSize = -1L,
            ),
        )
        val length = dataSource.open(DataSpec(Uri.parse("smb://stream/test.mp4")))
        val buffer = ByteArray(4)

        val read = dataSource.read(buffer, 0, buffer.size)
        val eof = dataSource.read(buffer, 0, buffer.size)

        assertEquals(C.LENGTH_UNSET.toLong(), length)
        assertEquals(3, read)
        assertArrayEquals("xyz".toByteArray(), buffer.copyOf(read))
        assertEquals(C.RESULT_END_OF_INPUT, eof)
        dataSource.close()
    }

    private class FakeRemoteReadableFile(
        private val bytes: ByteArray,
        private val reportedSize: Long = bytes.size.toLong(),
    ) : RemoteReadableFile {
        override val size: Long = reportedSize

        override suspend fun readAt(
            position: Long,
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (position >= bytes.size) {
                return -1
            }
            val available = bytes.size - position.toInt()
            val count = minOf(length, available)
            bytes.copyInto(
                destination = buffer,
                destinationOffset = offset,
                startIndex = position.toInt(),
                endIndex = position.toInt() + count,
            )
            return count
        }

        override suspend fun close() = Unit
    }
}
