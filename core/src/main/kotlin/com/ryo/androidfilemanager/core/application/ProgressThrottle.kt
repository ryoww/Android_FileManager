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
    // 転送は IO スレッド上で通知されるため、直前の判定結果が別スレッドから見えるよう volatile にする
    @Volatile
    private var lastEmittedAt: Long? = null

    fun shouldEmit(progress: TransferProgress): Boolean {
        if (progress.isComplete) {
            // 完了で間引きをリセットする。複数ファイル転送で次のファイルの開始フレームが
            // 完了直後に来ても握り潰さないため
            lastEmittedAt = null
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
