package com.ryo.androidfilemanager.data.cache

interface CacheRepository {
    suspend fun getTotalCacheSize(): Long
    suspend fun getSmbCacheSize(): Long
    suspend fun getThumbnailCacheSize(): Long

    suspend fun clearAll()
    suspend fun clearSmbCache()
    suspend fun clearThumbnailCache()
}

