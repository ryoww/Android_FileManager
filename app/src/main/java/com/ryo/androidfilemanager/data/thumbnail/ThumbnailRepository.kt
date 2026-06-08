package com.ryo.androidfilemanager.data.thumbnail

import com.ryo.androidfilemanager.data.model.FileItem

interface ThumbnailRepository {
    suspend fun getThumbnail(file: FileItem): ThumbnailResult
    suspend fun generateThumbnail(file: FileItem): ThumbnailResult
    suspend fun clearThumbnailCache()
}

