package com.ryo.androidfilemanager.data.thumbnail

import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.SourceType
import com.ryo.androidfilemanager.data.model.ViewerType
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbThumbnailRequestCoordinatorTest {

    private var now = 0L
    private val coordinator = SmbThumbnailRequestCoordinator(
        elapsedRealtime = { now },
        failedKeyTtlMs = FAILED_TTL_MS,
    )

    // --- viewport 世代の順序逆転(スクロール停止後にサムネイルが出なくなる競合)の再現 ---

    @Test
    fun `古い世代のviewport適用は破棄され新しい可視セットを壊さない`() = runTest {
        val olderGeneration = coordinator.newViewportGeneration()
        val newerGeneration = coordinator.newViewportGeneration()

        // 新しい viewport(B が可視)が先に適用される
        assertNotNull(
            coordinator.applyViewportSnapshot(newerGeneration, setOf("B")),
        )
        assertTrue(coordinator.enqueue(videoFile("B"), "B", ThumbnailRequestPriority.Visible))

        // 遅れて届いた古い viewport(A が可視)は破棄される
        assertNull(
            coordinator.applyViewportSnapshot(olderGeneration, setOf("A")),
        )

        // B の pending リクエストは生き残っている
        val request = coordinator.receive(ViewerType.Video)
        assertEquals("B", request.key)

        // 古い viewport の A は可視でないため enqueue できない
        assertFalse(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))
    }

    @Test
    fun `viewport変更で可視でなくなったpendingは削除される`() = runTest {
        val first = coordinator.newViewportGeneration()
        assertNotNull(coordinator.applyViewportSnapshot(first, setOf("A")))
        assertTrue(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))

        val second = coordinator.newViewportGeneration()
        assertNotNull(coordinator.applyViewportSnapshot(second, setOf("B")))
        assertTrue(coordinator.enqueue(videoFile("B"), "B", ThumbnailRequestPriority.Visible))

        // B のみ受信でき、A は pending から消えている
        assertEquals("B", coordinator.receive(ViewerType.Video).key)
        val extra = withTimeoutOrNull(1_000L) { coordinator.receive(ViewerType.Video) }
        assertNull(extra)
    }

    // --- failedKeys の TTL ---

    @Test
    fun `失敗したキーはTTL内は再enqueueされずTTL経過後に再試行できる`() = runTest {
        assertTrue(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))
        val request = coordinator.receive(ViewerType.Video)
        assertTrue(coordinator.markRunning(request))
        coordinator.finish(request, succeeded = false, retryable = false)

        assertFalse(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))

        now += FAILED_TTL_MS + 1L
        assertTrue(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))
    }

    @Test
    fun `retryableな失敗は即座に再enqueueできる`() = runTest {
        assertTrue(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))
        val request = coordinator.receive(ViewerType.Video)
        assertTrue(coordinator.markRunning(request))
        val stillWanted = coordinator.finish(request, succeeded = false, retryable = true)

        assertTrue(stillWanted)
        assertTrue(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))
    }

    @Test
    fun `viewport外で完了した非retryable失敗もTTL中は再enqueueされない`() = runTest {
        val first = coordinator.newViewportGeneration()
        assertNotNull(coordinator.applyViewportSnapshot(first, setOf("A")))
        assertTrue(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))
        val request = coordinator.receive(ViewerType.Video)
        assertTrue(coordinator.markRunning(request))

        val second = coordinator.newViewportGeneration()
        assertNotNull(coordinator.applyViewportSnapshot(second, setOf("B")))
        coordinator.finish(request, succeeded = false, retryable = false)

        val third = coordinator.newViewportGeneration()
        assertNotNull(coordinator.applyViewportSnapshot(third, setOf("A")))
        assertFalse(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))
    }

    // --- 実行中の重複防止と stale 判定 ---

    @Test
    fun `実行中のキーは重複enqueueされずfinish後に再enqueueできる`() = runTest {
        assertTrue(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))
        val request = coordinator.receive(ViewerType.Video)
        assertTrue(coordinator.markRunning(request))

        assertFalse(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))

        // 成功で終了 → failed 扱いにならず再 enqueue 可能
        coordinator.finish(request, succeeded = true, retryable = false)
        assertTrue(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))
    }

    @Test
    fun `viewport外へ出たリクエストはmarkRunningで拒否される`() = runTest {
        val first = coordinator.newViewportGeneration()
        assertNotNull(coordinator.applyViewportSnapshot(first, setOf("A")))
        assertTrue(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))
        val request = coordinator.receive(ViewerType.Video)

        val second = coordinator.newViewportGeneration()
        assertNotNull(coordinator.applyViewportSnapshot(second, setOf("B")))

        assertFalse(coordinator.markRunning(request))
    }

    // --- 実行中キャンセルハンドルの管理 ---

    @Test
    fun `viewport外になった実行中ソースのキャンセルが返される`() = runTest {
        val first = coordinator.newViewportGeneration()
        assertNotNull(coordinator.applyViewportSnapshot(first, setOf("A")))
        var cancelled = false
        assertTrue(coordinator.registerRunningCancellable("A") { cancelled = true })

        val second = coordinator.newViewportGeneration()
        val cancellations = coordinator.applyViewportSnapshot(second, setOf("B"))

        assertNotNull(cancellations)
        assertEquals(1, cancellations!!.size)
        cancellations.forEach { it() }
        assertTrue(cancelled)
    }

    @Test
    fun `可視でないキーのキャンセルハンドル登録は拒否される`() = runTest {
        val generation = coordinator.newViewportGeneration()
        assertNotNull(coordinator.applyViewportSnapshot(generation, setOf("B")))

        assertFalse(coordinator.registerRunningCancellable("A") { })
    }

    // --- 優先度とキューの基本動作 ---

    @Test
    fun `Visible優先度はBackgroundより先に処理される`() = runTest {
        // viewport snapshot なし(起動直後)の状態では Background も enqueue できる
        assertTrue(coordinator.enqueue(videoFile("bg"), "bg", ThumbnailRequestPriority.Background))
        assertTrue(coordinator.enqueue(videoFile("vis"), "vis", ThumbnailRequestPriority.Visible))

        assertEquals("vis", coordinator.receive(ViewerType.Video).key)
        assertEquals("bg", coordinator.receive(ViewerType.Video).key)
    }

    @Test
    fun `viewportの先頭にある動画が同じVisible内でも先に処理される`() = runTest {
        val generation = coordinator.newViewportGeneration()
        assertNotNull(
            coordinator.applyViewportSnapshot(
                generation,
                listOf("top", "second"),
            ),
        )

        // 投入順が逆でも、画面上の並び順を優先する。
        assertTrue(coordinator.enqueue(videoFile("second"), "second", ThumbnailRequestPriority.Visible))
        assertTrue(coordinator.enqueue(videoFile("top"), "top", ThumbnailRequestPriority.Visible))

        assertEquals("top", coordinator.receive(ViewerType.Video).key)
        assertEquals("second", coordinator.receive(ViewerType.Video).key)
    }

    @Test
    fun `viewport確定前にBackgroundで入った動画も表示順に再整列される`() = runTest {
        assertTrue(coordinator.enqueue(videoFile("second"), "second", ThumbnailRequestPriority.Background))
        assertTrue(coordinator.enqueue(videoFile("top"), "top", ThumbnailRequestPriority.Background))

        val generation = coordinator.newViewportGeneration()
        assertNotNull(
            coordinator.applyViewportSnapshot(
                generation,
                listOf("top", "second"),
            ),
        )

        assertEquals("top", coordinator.receive(ViewerType.Video).key)
        assertEquals("second", coordinator.receive(ViewerType.Video).key)
    }

    @Test
    fun `同一キーの重複enqueueはマージされ1件だけ受信される`() = runTest {
        assertTrue(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Background))
        assertTrue(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))

        val request = coordinator.receive(ViewerType.Video)
        assertEquals("A", request.key)
        assertEquals(ThumbnailRequestPriority.Visible, request.priority)

        val extra = withTimeoutOrNull(1_000L) { coordinator.receive(ViewerType.Video) }
        assertNull(extra)
    }

    @Test
    fun `ViewerTypeごとにキューが分かれている`() = runTest {
        assertTrue(coordinator.enqueue(videoFile("v"), "v", ThumbnailRequestPriority.Visible))
        assertTrue(coordinator.enqueue(pdfFile("p"), "p", ThumbnailRequestPriority.Visible))

        assertEquals("p", coordinator.receive(ViewerType.Pdf).key)
        assertEquals("v", coordinator.receive(ViewerType.Video).key)
    }

    // --- 複数ワーカーでの全件処理 ---

    @Test
    fun `2ワーカーでN件のリクエストが全件処理される`() = runTest {
        val keys = (1..8).map { "file$it" }
        keys.forEach { key ->
            assertTrue(coordinator.enqueue(videoFile(key), key, ThumbnailRequestPriority.Visible))
        }

        val processed = mutableSetOf<String>()
        val processedMutex = Mutex()
        val workers = List(2) {
            launch {
                while (true) {
                    val request = coordinator.receive(ViewerType.Video)
                    if (!coordinator.markRunning(request)) {
                        continue
                    }
                    coordinator.finish(request, succeeded = true, retryable = false)
                    processedMutex.withLock { processed += request.key }
                }
            }
        }

        // 全件処理されるまで仮想時間を進めつつ待つ
        withTimeoutOrNull(10_000L) {
            while (processedMutex.withLock { processed.size } < keys.size) {
                kotlinx.coroutines.yield()
            }
        }
        workers.forEach { it.cancel() }

        assertEquals(keys.toSet(), processed)
    }

    @Test
    fun `clearFailedで失敗キーがリセットされ再enqueueできる`() = runTest {
        assertTrue(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))
        val request = coordinator.receive(ViewerType.Video)
        assertTrue(coordinator.markRunning(request))
        coordinator.finish(request, succeeded = false, retryable = false)
        assertFalse(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))

        coordinator.clearFailed()

        assertTrue(coordinator.enqueue(videoFile("A"), "A", ThumbnailRequestPriority.Visible))
    }

    private fun videoFile(name: String): FileItem = smbFile("$name.mp4", "video/mp4")

    private fun pdfFile(name: String): FileItem = smbFile("$name.pdf", "application/pdf")

    private fun smbFile(name: String, mimeType: String): FileItem = FileItem(
        name = name,
        path = "share/$name",
        uri = null,
        isDirectory = false,
        size = 1_000L,
        modifiedAt = 1_000L,
        mimeType = mimeType,
        sourceType = SourceType.SMB,
    )

    private companion object {
        const val FAILED_TTL_MS = 5L * 60L * 1000L
    }
}
