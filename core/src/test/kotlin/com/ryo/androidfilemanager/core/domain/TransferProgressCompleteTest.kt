package com.ryo.androidfilemanager.core.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferProgressCompleteTest {
    @Test
    fun isCompleteTrueWhenBytesReachTotal() {
        val progress = TransferProgress(
            kind = TransferKind.DOWNLOAD,
            fileName = "a.txt",
            bytesTransferred = 100,
            totalBytes = 100,
        )

        assertTrue(progress.isComplete)
    }

    @Test
    fun isCompleteFalseWhenBytesBelowTotal() {
        val progress = TransferProgress(
            kind = TransferKind.DOWNLOAD,
            fileName = "a.txt",
            bytesTransferred = 50,
            totalBytes = 100,
        )

        assertFalse(progress.isComplete)
    }

    @Test
    fun isCompleteFalseWhenTotalBytesUnknown() {
        val progress = TransferProgress(
            kind = TransferKind.DOWNLOAD,
            fileName = "a.txt",
            bytesTransferred = 50,
            totalBytes = null,
        )

        assertFalse(progress.isComplete)
    }
}
