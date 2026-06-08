package com.ryo.androidfilemanager.data.cache

import android.content.Context
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileCacheRepository(
    context: Context,
) : CacheRepository {
    private val appContext = context.applicationContext
    private val smbCacheDir = File(appContext.cacheDir, SMB_CACHE_DIR)
    private val thumbnailCacheDir = File(appContext.cacheDir, THUMBNAIL_CACHE_DIR)
    private val tempDir = File(appContext.cacheDir, TEMP_DIR)

    override suspend fun getTotalCacheSize(): Long = withContext(Dispatchers.IO) {
        calculateDirectorySize(smbCacheDir) +
            calculateDirectorySize(thumbnailCacheDir) +
            calculateDirectorySize(tempDir)
    }

    override suspend fun getSmbCacheSize(): Long = withContext(Dispatchers.IO) {
        calculateDirectorySize(smbCacheDir)
    }

    override suspend fun getThumbnailCacheSize(): Long = withContext(Dispatchers.IO) {
        calculateDirectorySize(thumbnailCacheDir)
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            clearDirectory(smbCacheDir)
            clearDirectory(thumbnailCacheDir)
            clearDirectory(tempDir)
        }
    }

    override suspend fun clearSmbCache() {
        withContext(Dispatchers.IO) {
            clearDirectory(smbCacheDir)
        }
    }

    override suspend fun clearThumbnailCache() {
        withContext(Dispatchers.IO) {
            clearDirectory(thumbnailCacheDir)
        }
    }

    private companion object {
        const val SMB_CACHE_DIR = "smb_cache"
        const val THUMBNAIL_CACHE_DIR = "thumbnails"
        const val TEMP_DIR = "temp"
    }
}

fun calculateDirectorySize(file: File): Long {
    if (!file.exists()) {
        return 0L
    }

    if (file.isFile) {
        return file.length()
    }

    return file.listFiles()
        ?.sumOf { child -> calculateDirectorySize(child) }
        ?: 0L
}

fun clearDirectory(directory: File) {
    if (directory.exists()) {
        directory.deleteRecursively()
    }
    directory.mkdirs()
}

fun formatCacheSize(bytes: Long): String {
    if (bytes < 1024) {
        return "$bytes B"
    }

    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var index = 0

    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }

    return String.format(Locale.US, "%.1f %s", value, units[index])
}

