package com.ryo.androidfilemanager.data.smb

import com.ryo.androidfilemanager.core.application.port.RemoteReadableFile

import java.io.IOException
import kotlin.math.min
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * readAt が 1 回あたり最大 [maxReadPerCall] バイトしか返さない fake。
 * データは決定的パターン(`index -> (index % 251).toByte()`)で埋める。
 * 呼び出し回数・要求範囲を記録し、テストからチャンク再取得の有無を検証できるようにする。
 */
private class FakeRemoteReadableFile(
    override val size: Long,
    private val maxReadPerCall: Int = 100,
) : RemoteReadableFile {
    val data: ByteArray = ByteArray(size.toInt()) { index -> (index % 251).toByte() }
    val requestedRanges = mutableListOf<Pair<Long, Int>>()
    var closed = false
        private set

    override suspend fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        requestedRanges += position to length
        if (position >= size) return 0
        val toCopy = min(min(length, maxReadPerCall).toLong(), size - position).toInt()
        System.arraycopy(data, position.toInt(), buffer, offset, toCopy)
        return toCopy
    }

    override suspend fun close() {
        closed = true
    }
}

class ChunkedRemoteReaderTest {

    @Test
    fun `read across chunk boundary matches source bytes`() {
        val fileSize = 1000L
        val chunkSize = 300L
        val fake = FakeRemoteReadableFile(size = fileSize)
        val reader = ChunkedRemoteReader(
            remote = fake,
            readBudgetBytes = Long.MAX_VALUE,
            chunkSize = chunkSize,
        )

        // chunk 0 は [0, 300)、chunk 1 は [300, 600) なので 250..450 は境界をまたぐ
        val dest = ByteArray(200)
        val read = reader.read(position = 250, dest = dest, destOffset = 0, length = 200)

        assertEquals(200, read)
        val expected = fake.data.copyOfRange(250, 450)
        assertArrayEquals(expected, dest)
    }

    @Test
    fun `read of final undersized chunk returns correct length and content`() {
        val fileSize = 1050L
        val chunkSize = 300L // 最終チャンクは [900, 1050) = 150 バイトのみ
        val fake = FakeRemoteReadableFile(size = fileSize)
        val reader = ChunkedRemoteReader(
            remote = fake,
            readBudgetBytes = Long.MAX_VALUE,
            chunkSize = chunkSize,
        )

        val dest = ByteArray(200)
        val read = reader.read(position = 950, dest = dest, destOffset = 0, length = 200)

        assertEquals(100, read)
        val expected = fake.data.copyOfRange(950, 1050)
        assertArrayEquals(expected, dest.copyOfRange(0, 100))
    }

    @Test
    fun `read at or beyond end of file returns zero`() {
        val fileSize = 500L
        val fake = FakeRemoteReadableFile(size = fileSize)
        val reader = ChunkedRemoteReader(
            remote = fake,
            readBudgetBytes = Long.MAX_VALUE,
            chunkSize = 300L,
        )

        val dest = ByteArray(50)
        assertEquals(0, reader.read(position = 500, dest = dest, destOffset = 0, length = 50))
        assertEquals(0, reader.read(position = 600, dest = dest, destOffset = 0, length = 50))
    }

    @Test
    fun `budget exceeded throws IOException with fetched and budget details`() {
        val fileSize = 10_000L
        val chunkSize = 100L
        val fake = FakeRemoteReadableFile(size = fileSize)
        val budget = 250L
        val reader = ChunkedRemoteReader(
            remote = fake,
            readBudgetBytes = budget,
            chunkSize = chunkSize,
        )

        val dest = ByteArray(50)
        try {
            // 各 read は新しいチャンクを要求させるため chunkSize 間隔で position をずらす
            var position = 0L
            repeat(10) {
                reader.read(position = position, dest = dest, destOffset = 0, length = 50)
                position += chunkSize
            }
            fail("Expected IOException due to budget exceeded")
        } catch (e: IOException) {
            assertTrue("message should mention fetched bytes: ${e.message}", e.message!!.contains(reader.fetchedBytes.toString()))
            assertTrue("message should mention budget: ${e.message}", e.message!!.contains(budget.toString()))
        }
    }

    @Test
    fun `cancelled reader throws IOException on read`() {
        val fake = FakeRemoteReadableFile(size = 1000L)
        var cancelled = false
        val reader = ChunkedRemoteReader(
            remote = fake,
            readBudgetBytes = Long.MAX_VALUE,
            chunkSize = 300L,
            isCancelled = { cancelled },
        )

        cancelled = true
        val dest = ByteArray(50)
        try {
            reader.read(position = 0, dest = dest, destOffset = 0, length = 50)
            fail("Expected IOException due to cancellation")
        } catch (e: IOException) {
            // expected
        }
    }

    @Test
    fun `evicted chunk is refetched increasing fetchedBytes`() {
        val fileSize = 1000L
        val chunkSize = 100L
        val fake = FakeRemoteReadableFile(size = fileSize)
        val reader = ChunkedRemoteReader(
            remote = fake,
            readBudgetBytes = Long.MAX_VALUE,
            chunkSize = chunkSize,
            maxCachedChunks = 2,
        )

        val dest = ByteArray(50)
        // チャンク 0 → 1 → 2 の順で読み、キャッシュ容量(2)を超えさせて 0 を evict する
        reader.read(position = 0, dest = dest, destOffset = 0, length = 50) // chunk 0
        reader.read(position = 100, dest = dest, destOffset = 0, length = 50) // chunk 1
        reader.read(position = 200, dest = dest, destOffset = 0, length = 50) // chunk 2 (chunk 0 evicted)

        val fetchedAfterThreeChunks = reader.fetchedBytes
        assertEquals(300L, fetchedAfterThreeChunks)

        // chunk 0 を再度読む -> evict されているので再取得され fetchedBytes が増える
        reader.read(position = 0, dest = dest, destOffset = 0, length = 50)
        assertEquals(fetchedAfterThreeChunks + chunkSize, reader.fetchedBytes)
    }

    @Test
    fun `repeated read of same chunk is cache hit and does not increase fetchedBytes`() {
        val fileSize = 1000L
        val chunkSize = 300L
        val fake = FakeRemoteReadableFile(size = fileSize)
        val reader = ChunkedRemoteReader(
            remote = fake,
            readBudgetBytes = Long.MAX_VALUE,
            chunkSize = chunkSize,
        )

        val dest = ByteArray(50)
        reader.read(position = 10, dest = dest, destOffset = 0, length = 50)
        val fetchedAfterFirstRead = reader.fetchedBytes
        assertEquals(chunkSize, fetchedAfterFirstRead)

        // 同一チャンク内の別 position を複数回読んでもキャッシュヒットで fetchedBytes は増えない
        reader.read(position = 20, dest = dest, destOffset = 0, length = 50)
        reader.read(position = 100, dest = dest, destOffset = 0, length = 50)
        reader.read(position = 200, dest = dest, destOffset = 0, length = 50)

        assertEquals(fetchedAfterFirstRead, reader.fetchedBytes)
    }
}
