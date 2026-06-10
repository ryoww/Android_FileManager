package com.ryo.androidfilemanager.data.thumbnail

import com.ryo.androidfilemanager.data.model.FileItem
import kotlinx.coroutines.flow.StateFlow

interface ThumbnailRepository {
    suspend fun getThumbnail(file: FileItem): ThumbnailResult
    suspend fun generateThumbnail(file: FileItem): ThumbnailResult
    fun requestThumbnail(file: FileItem)
    fun observeThumbnailVersion(): StateFlow<Long>
    suspend fun clearThumbnailCache()
}
