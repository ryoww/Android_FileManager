package com.ryo.androidfilemanager.data.thumbnail

import org.junit.Assert.assertEquals
import org.junit.Test

class SmbVideoReadProfileTest {

    @Test
    fun `WebMとMKVは連続読み取り向けのlarge chunkを使う`() {
        assertEquals(
            SmbVideoReadProfile(
                chunkSize = 2L * 1024L * 1024L,
                maxCachedChunks = 16,
                renderTimeoutMs = 6_000L,
            ),
            smbVideoReadProfile("sample.webm"),
        )
        assertEquals(
            SmbVideoReadProfile(
                chunkSize = 2L * 1024L * 1024L,
                maxCachedChunks = 16,
                renderTimeoutMs = 6_000L,
            ),
            smbVideoReadProfile("sample.MKV"),
        )
    }

    @Test
    fun `MP4はランダムアクセス向けのsmall chunkを維持する`() {
        assertEquals(
            SmbVideoReadProfile(
                chunkSize = 512L * 1024L,
                maxCachedChunks = 48,
                renderTimeoutMs = 12_000L,
            ),
            smbVideoReadProfile("sample.mp4"),
        )
    }
}
