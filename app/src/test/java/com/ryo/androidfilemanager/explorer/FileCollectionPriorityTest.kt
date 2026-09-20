package com.ryo.androidfilemanager.explorer

import org.junit.Assert.assertEquals
import org.junit.Test

class FileCollectionPriorityTest {

    @Test
    fun `defaultでは画面外itemをthumbnail対象に含めない`() {
        val files = (0..9).toList()

        val prioritized = itemsInThumbnailPriorityOrder(
            items = files,
            firstVisibleIndex = 4,
            lastVisibleIndex = 5,
        )

        assertEquals(listOf(4, 5), prioritized)
    }

    @Test
    fun `実表示を先頭にして下方向と上方向のbufferを後ろへ並べる`() {
        val files = (0..19).toList()

        val prioritized = itemsInThumbnailPriorityOrder(
            items = files,
            firstVisibleIndex = 8,
            lastVisibleIndex = 10,
            bufferSize = 2,
        )

        assertEquals(listOf(8, 9, 10, 11, 12, 6, 7), prioritized)
    }

    @Test
    fun `末尾付近でも重複せず範囲内だけを返す`() {
        val files = (0..5).toList()

        val prioritized = itemsInThumbnailPriorityOrder(
            items = files,
            firstVisibleIndex = 4,
            lastVisibleIndex = 5,
            bufferSize = 3,
        )

        assertEquals(listOf(4, 5, 1, 2, 3), prioritized)
    }
}
