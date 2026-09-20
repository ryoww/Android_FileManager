package com.ryo.androidfilemanager.data.thumbnail

import com.ryo.androidfilemanager.data.model.FileItem
import kotlinx.coroutines.flow.StateFlow

interface ThumbnailRepository {
    suspend fun getThumbnail(file: FileItem): ThumbnailResult
    suspend fun generateThumbnail(file: FileItem): ThumbnailResult
    fun requestThumbnail(file: FileItem)
    fun updateVisibleThumbnails(files: List<FileItem>) {
        files.forEach { file -> requestThumbnail(file) }
    }
    fun observeThumbnailVersion(): StateFlow<Long>

    /**
     * 失敗として記録されたサムネイルの再試行ブロックを解除する。
     * ユーザーの明示的なリロード操作で呼び、TTL 満了を待たずに再生成させる。
     */
    fun resetFailedThumbnails() {}

    suspend fun clearThumbnailCache()
}
