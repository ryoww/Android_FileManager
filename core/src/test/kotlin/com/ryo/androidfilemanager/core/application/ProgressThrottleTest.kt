package com.ryo.androidfilemanager.core.application

import com.ryo.androidfilemanager.core.domain.TransferKind
import com.ryo.androidfilemanager.core.domain.TransferProgress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressThrottleTest {
    @Test
    fun firstEmitIsAlwaysAllowed() {
        var clock = 0L
        val throttle = ProgressThrottle(intervalMs = 100L, now = { clock })

        assertTrue(throttle.shouldEmit(progress(bytesTransferred = 10, totalBytes = 100)))
    }

    @Test
    fun emitWithinIntervalIsSuppressedThenAllowedAfterInterval() {
        var clock = 0L
        val throttle = ProgressThrottle(intervalMs = 100L, now = { clock })
        throttle.shouldEmit(progress(bytesTransferred = 10, totalBytes = 100))

        clock = 50L
        assertFalse(throttle.shouldEmit(progress(bytesTransferred = 20, totalBytes = 100)))

        clock = 101L
        assertTrue(throttle.shouldEmit(progress(bytesTransferred = 30, totalBytes = 100)))
    }

    @Test
    fun completeProgressIsAlwaysEmittedEvenWithinInterval() {
        var clock = 0L
        val throttle = ProgressThrottle(intervalMs = 100L, now = { clock })
        throttle.shouldEmit(progress(bytesTransferred = 10, totalBytes = 100))

        clock = 10L
        assertTrue(throttle.shouldEmit(progress(bytesTransferred = 100, totalBytes = 100)))
    }

    private fun progress(bytesTransferred: Long, totalBytes: Long?): TransferProgress = TransferProgress(
        kind = TransferKind.DOWNLOAD,
        fileName = "a.txt",
        bytesTransferred = bytesTransferred,
        totalBytes = totalBytes,
    )
}
