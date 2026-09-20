package com.ryo.androidfilemanager.core.application

import com.ryo.androidfilemanager.core.domain.TransferProgress

/**
 * 進捗通知を intervalMs 間隔に間引く。UI 更新頻度を絞ってスクロール性能を落とさないため。
 * 完了通知（isComplete）は間隔を無視して必ず通す。
 */
class ProgressThrottle(
    private val intervalMs: Long = 100L,
    private val now: () -> Long,
) {
    private var lastEmittedAt: Long? = null

    fun shouldEmit(progress: TransferProgress): Boolean {
        if (progress.isComplete) {
            lastEmittedAt = now()
            return true
        }

        val last = lastEmittedAt
        val currentTime = now()
        if (last != null && currentTime - last < intervalMs) {
            return false
        }

        lastEmittedAt = currentTime
        return true
    }
}
