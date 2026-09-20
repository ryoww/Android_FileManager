package com.ryo.androidfilemanager.data.thumbnail

import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.ViewerType
import com.ryo.androidfilemanager.data.source.detectViewerType
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SmbThumbnailRequestCoordinator(
    private val elapsedRealtime: () -> Long,
    private val failedKeyTtlMs: Long,
) {
    private val mutex = Mutex()
    private val queues = mapOf(
        ViewerType.Pdf to PrioritizedSmbThumbnailQueue(),
        ViewerType.Image to PrioritizedSmbThumbnailQueue(),
        ViewerType.Video to PrioritizedSmbThumbnailQueue(),
    )
    private val runningKeys = mutableSetOf<String>()
    private val visibleKeys = mutableSetOf<String>()
    private val visibleRanks = mutableMapOf<String, Int>()
    private val failedKeys = mutableMapOf<String, Long>()
    private val runningCancellables = mutableMapOf<String, () -> Unit>()
    private var hasViewportSnapshot = false
    private val viewportGenerationCounter = AtomicLong(0L)
    private var latestAppliedViewportGeneration = 0L

    fun newViewportGeneration(): Long = viewportGenerationCounter.incrementAndGet()

    suspend fun applyViewportSnapshot(
        generation: Long,
        nextVisibleKeys: Collection<String>,
    ): List<() -> Unit>? = mutex.withLock {
        // 世代チェック・pending 削除・可視セット更新は単一クリティカルセクションで行う。
        // 分割すると、並行する古い世代の適用が新しい世代の pending を削除したり
        // 可視セットを古い内容で上書きしたりできてしまう(サムネイル生成が
        // 再スクロールまで止まる原因になる)
        if (generation <= latestAppliedViewportGeneration) {
            return null
        }
        latestAppliedViewportGeneration = generation

        val nextVisibleKeyList = nextVisibleKeys.toList().distinct()
        val nextVisibleKeySet = nextVisibleKeyList.toSet()
        val nextVisibleRanks = nextVisibleKeyList
            .withIndex()
            .associate { (index, key) -> key to index }
        queues.values.forEach { queue ->
            queue.removePendingNotIn(nextVisibleKeySet)
            queue.updateViewportRanks(nextVisibleRanks)
        }

        hasViewportSnapshot = true
        visibleKeys.clear()
        visibleKeys += nextVisibleKeySet
        visibleRanks.clear()
        visibleRanks += nextVisibleRanks
        failedKeys.pruneExpiredLocked(elapsedRealtime())

        runningCancellables
            .filterKeys { key -> key !in visibleKeys }
            .values
            .toList()
    }

    suspend fun enqueue(
        file: FileItem,
        key: String,
        requestedPriority: ThumbnailRequestPriority,
    ): Boolean = mutex.withLock {
        val viewerType = detectViewerType(file.name, file.mimeType)
        val queue = viewerType.requestQueueOrNull() ?: return@withLock false
        val now = elapsedRealtime()
        if (isRecentlyFailedLocked(key, now) || key in runningKeys) {
            return@withLock false
        }
        if (hasViewportSnapshot && key !in visibleKeys) {
            return@withLock false
        }

        queue.enqueueOrUpdate(
            QueuedSmbThumbnailRequest(
                file = file,
                key = key,
                viewerType = viewerType,
                priority = if (key in visibleRanks) {
                    ThumbnailRequestPriority.Visible
                } else {
                    requestedPriority
                },
                viewportGeneration = latestAppliedViewportGeneration,
                enqueuedAt = now,
                viewportRank = visibleRanks[key] ?: Int.MAX_VALUE,
            ),
        )
        true
    }

    suspend fun receive(viewerType: ViewerType): QueuedSmbThumbnailRequest =
        viewerType.requestQueue().receive()

    suspend fun markRunning(request: QueuedSmbThumbnailRequest): Boolean {
        val now = elapsedRealtime()
        return mutex.withLock {
            if (isStaleLocked(request) || isRecentlyFailedLocked(request.key, now)) {
                false
            } else {
                runningKeys += request.key
                true
            }
        }
    }

    suspend fun finish(
        request: QueuedSmbThumbnailRequest,
        succeeded: Boolean,
        retryable: Boolean,
    ): Boolean {
        val now = elapsedRealtime()
        return mutex.withLock {
            val stale = isStaleLocked(request)
            runningKeys -= request.key
            runningCancellables.remove(request.key)
            if (!succeeded && !retryable) {
                failedKeys[request.key] = now
            }
            !stale
        }
    }

    suspend fun registerRunningCancellable(
        key: String,
        cancel: () -> Unit,
    ): Boolean =
        mutex.withLock {
            if (hasViewportSnapshot && key !in visibleKeys) {
                false
            } else {
                runningCancellables[key] = cancel
                true
            }
        }

    suspend fun unregisterRunningCancellable(key: String) {
        mutex.withLock {
            runningCancellables.remove(key)
        }
    }

    suspend fun clearFailed() {
        mutex.withLock {
            failedKeys.clear()
        }
    }

    private fun isStaleLocked(request: QueuedSmbThumbnailRequest): Boolean =
        hasViewportSnapshot && request.key !in visibleKeys

    private fun isRecentlyFailedLocked(key: String, now: Long): Boolean {
        val failedAt = failedKeys[key] ?: return false
        return if (now - failedAt <= failedKeyTtlMs) {
            true
        } else {
            failedKeys.remove(key)
            false
        }
    }

    private fun MutableMap<String, Long>.pruneExpiredLocked(now: Long) {
        entries.removeAll { (_, failedAt) -> now - failedAt > failedKeyTtlMs }
    }

    private fun ViewerType.requestQueue(): PrioritizedSmbThumbnailQueue =
        requestQueueOrNull() ?: error("Unsupported SMB thumbnail type: $this")

    private fun ViewerType.requestQueueOrNull(): PrioritizedSmbThumbnailQueue? =
        queues[this]
}

internal data class QueuedSmbThumbnailRequest(
    val file: FileItem,
    val key: String,
    val viewerType: ViewerType,
    val priority: ThumbnailRequestPriority,
    val viewportGeneration: Long,
    val enqueuedAt: Long,
    val viewportRank: Int = Int.MAX_VALUE,
) {
    fun mergedWith(next: QueuedSmbThumbnailRequest): QueuedSmbThumbnailRequest = next.copy(
        enqueuedAt = enqueuedAt,
        priority = if (next.priority.rank > priority.rank) next.priority else priority,
        viewportRank = minOf(viewportRank, next.viewportRank),
    )
}

internal enum class ThumbnailRequestPriority(val rank: Int) {
    Background(0),
    Visible(10),
}

internal class PrioritizedSmbThumbnailQueue {
    private val mutex = Mutex()
    private val pending = LinkedHashMap<String, QueuedSmbThumbnailRequest>()
    private val signal = Channel<Unit>(Channel.CONFLATED)

    suspend fun enqueueOrUpdate(request: QueuedSmbThumbnailRequest) {
        mutex.withLock {
            val current = pending[request.key]
            pending[request.key] = current?.mergedWith(request) ?: request
        }
        signal.trySend(Unit)
    }

    suspend fun receive(): QueuedSmbThumbnailRequest {
        while (true) {
            val next = mutex.withLock {
                val request = pending.values
                    .minWithOrNull(
                        compareByDescending<QueuedSmbThumbnailRequest> { it.priority.rank }
                            .thenBy { it.viewportRank }
                            .thenBy { it.enqueuedAt },
                    )
                if (request != null) {
                    pending.remove(request.key)
                }
                request
            }
            if (next != null) {
                return next
            }
            signal.receive()
        }
    }

    suspend fun removePendingNotIn(allowedKeys: Set<String>): Set<String> =
        mutex.withLock {
            val removed = pending.keys
                .filter { key -> key !in allowedKeys }
                .toSet()
            removed.forEach { key -> pending.remove(key) }
            removed
        }

    suspend fun updateViewportRanks(ranks: Map<String, Int>) {
        mutex.withLock {
            pending.entries.forEach { (key, request) ->
                val rank = ranks[key]
                pending[key] = if (rank == null) {
                    request.copy(viewportRank = Int.MAX_VALUE)
                } else {
                    request.copy(
                        priority = ThumbnailRequestPriority.Visible,
                        viewportRank = rank,
                    )
                }
            }
        }
        signal.trySend(Unit)
    }
}
