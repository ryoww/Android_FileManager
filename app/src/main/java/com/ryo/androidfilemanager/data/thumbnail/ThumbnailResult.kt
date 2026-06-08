package com.ryo.androidfilemanager.data.thumbnail

import com.ryo.androidfilemanager.data.model.FileItem

sealed class ThumbnailResult {
    data class CachedFile(
        val key: String,
        val uri: String,
    ) : ThumbnailResult()

    data class Icon(
        val descriptor: IconDescriptor,
    ) : ThumbnailResult()

    data class Unavailable(
        val reason: String,
    ) : ThumbnailResult()
}

fun thumbnailKey(file: FileItem): String = thumbnailKey(
    sourceType = file.sourceType.name,
    pathOrUri = file.uri ?: file.path,
    size = file.size,
    modifiedAt = file.modifiedAt,
)

fun thumbnailKey(
    sourceType: String,
    pathOrUri: String,
    size: Long?,
    modifiedAt: Long?,
): String = listOf(sourceType, pathOrUri, size?.toString().orEmpty(), modifiedAt?.toString().orEmpty())
    .joinToString(separator = ":")
