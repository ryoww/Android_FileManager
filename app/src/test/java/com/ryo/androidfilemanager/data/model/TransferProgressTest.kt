package com.ryo.androidfilemanager.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferProgressTest {

    private fun progress(bytes: Long, total: Long?) = TransferProgress(
        kind = TransferKind.DOWNLOAD,
        fileName = "sample.bin",
        bytesTransferred = bytes,
        totalBytes = total,
    )

    @Test
    fun `totalBytes が既知なら fraction は bytes over total になる`() {
        val result = progress(bytes = 250L, total = 1000L).fraction

        assertEquals(0.25f, result!!, 0.0001f)
    }

    @Test
    fun `totalBytes が null なら fraction は null（不確定表示）`() {
        assertNull(progress(bytes = 100L, total = null).fraction)
    }

    @Test
    fun `totalBytes が 0 なら fraction は null`() {
        assertNull(progress(bytes = 0L, total = 0L).fraction)
    }

    @Test
    fun `bytesTransferred が totalBytes を超えたら fraction は 1 に丸まる`() {
        val result = progress(bytes = 1500L, total = 1000L).fraction

        assertEquals(1f, result!!, 0.0001f)
    }
}
