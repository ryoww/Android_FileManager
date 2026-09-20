package com.ryo.androidfilemanager.core.application

import java.io.ByteArrayOutputStream
import kotlin.math.min
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 決定的なバイト列 (`index -> (index % 251).toByte()`) を返す fake reader。
 * halfRead = true のときは要求長の半分（最低 1 バイト）しか返さず、部分読みを再現する。
 * 要求範囲を記録し、読み取り済み位置より先を要求していないか検証できるようにする。
 */
private class FakePositionedReader(
    size: Int,
    private val halfRead: Boolean = false,
) : PositionedReader {
    val data: ByteArray = ByteArray(size) { index -> (index % 251).toByte() }
    val requestedPositions = mutableListOf<Long>()

    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        requestedPositions += position
        if (position >= data.size) return -1
        val available = data.size - position.toInt()
        val toCopy = if (halfRead) {
            min(min(length / 2 + if (length % 2 == 0) 0 else 1, available), length)
        } else {
            min(length, available)
        }
        System.arraycopy(data, position.toInt(), buffer, offset, toCopy)
        return toCopy
    }
}

class ProgressCopyTest {

    @Test
    fun `チャンク境界をまたぐサイズでも出力が元データと完全一致する`() {
        val reader = FakePositionedReader(size = 2500)
        val output = ByteArrayOutputStream()

        val total = copyWithProgress(
            reader = reader,
            output = output,
            totalBytes = null,
            chunkSize = 1000,
            onProgress = {},
        )

        assertEquals(2500L, total)
        assertArrayEquals(reader.data, output.toByteArray())
    }

    @Test
    fun `onProgress は単調増加し最後の値がコピー総量と一致する`() {
        val reader = FakePositionedReader(size = 2500)
        val output = ByteArrayOutputStream()
        val reported = mutableListOf<Long>()

        val total = copyWithProgress(
            reader = reader,
            output = output,
            totalBytes = null,
            chunkSize = 1000,
            onProgress = { reported += it },
        )

        assertTrue(reported.isNotEmpty())
        for (i in 1 until reported.size) {
            assertTrue(
                "progress は単調増加であるべき: $reported",
                reported[i] > reported[i - 1],
            )
        }
        assertEquals(total, reported.last())
    }

    @Test
    fun `totalBytes が既知ならそこで止まり先の position を要求しない`() {
        val reader = FakePositionedReader(size = 2500)
        val output = ByteArrayOutputStream()

        val total = copyWithProgress(
            reader = reader,
            output = output,
            totalBytes = 1500L,
            chunkSize = 1000,
            onProgress = {},
        )

        assertEquals(1500L, total)
        assertArrayEquals(reader.data.copyOfRange(0, 1500), output.toByteArray())
        assertTrue(
            "1500 より先の position は要求してはいけない: ${reader.requestedPositions}",
            reader.requestedPositions.all { it < 1500L },
        )
    }

    @Test
    fun `totalBytes が null なら reader が EOF を返すまで読む`() {
        val reader = FakePositionedReader(size = 777)
        val output = ByteArrayOutputStream()

        val total = copyWithProgress(
            reader = reader,
            output = output,
            totalBytes = null,
            chunkSize = 300,
            onProgress = {},
        )

        assertEquals(777L, total)
        assertArrayEquals(reader.data, output.toByteArray())
    }

    @Test
    fun `部分読みの reader でも出力が完全一致する`() {
        val reader = FakePositionedReader(size = 2500, halfRead = true)
        val output = ByteArrayOutputStream()

        val total = copyWithProgress(
            reader = reader,
            output = output,
            totalBytes = null,
            chunkSize = 1000,
            onProgress = {},
        )

        assertEquals(2500L, total)
        assertArrayEquals(reader.data, output.toByteArray())
    }

    @Test
    fun `空データでは戻り値 0 で onProgress は 0 で1回だけ呼ばれる`() {
        val reader = FakePositionedReader(size = 0)
        val output = ByteArrayOutputStream()
        val reported = mutableListOf<Long>()

        val total = copyWithProgress(
            reader = reader,
            output = output,
            totalBytes = null,
            chunkSize = 1000,
            onProgress = { reported += it },
        )

        assertEquals(0L, total)
        assertEquals(listOf(0L), reported)
    }

    @Test
    fun `chunkSize が 0 以下なら IllegalArgumentException`() {
        val reader = FakePositionedReader(size = 10)
        val output = ByteArrayOutputStream()

        try {
            copyWithProgress(
                reader = reader,
                output = output,
                totalBytes = null,
                chunkSize = 0,
                onProgress = {},
            )
            fail("IllegalArgumentException を期待した")
        } catch (_: IllegalArgumentException) {
            // 期待通り
        }
    }
}
