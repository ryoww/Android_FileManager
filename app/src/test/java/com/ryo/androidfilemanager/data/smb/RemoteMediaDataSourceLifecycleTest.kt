package com.ryo.androidfilemanager.data.smb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteMediaDataSourceLifecycleTest {

    @Test
    fun `通常closeはviewportキャンセルとして扱わない`() {
        val lifecycle = RemoteMediaDataSourceLifecycle()

        lifecycle.close()

        assertFalse(lifecycle.canRead)
        assertFalse(lifecycle.isCancelled)
    }

    @Test
    fun `明示cancelだけがviewportキャンセルとして記録される`() {
        val lifecycle = RemoteMediaDataSourceLifecycle()

        lifecycle.cancel()

        assertFalse(lifecycle.canRead)
        assertTrue(lifecycle.isCancelled)
    }
}
