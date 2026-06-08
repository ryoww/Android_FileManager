package com.ryo.androidfilemanager.data.thumbnail

import android.content.Context
import android.net.Uri
import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.SourceType
import com.ryo.androidfilemanager.data.model.ViewerType
import com.ryo.androidfilemanager.data.source.SmbFileSource
import com.ryo.androidfilemanager.data.source.detectViewerType

class SmbThumbnailRepository(
    context: Context,
    private val sourceProvider: () -> SmbFileSource?,
) : ThumbnailRepository {
    private val localRepository = FileThumbnailRepository(context)

    override suspend fun getThumbnail(file: FileItem): ThumbnailResult {
        if (file.sourceType != SourceType.SMB) {
            return localRepository.getThumbnail(file)
        }

        if (file.isDirectory) {
            return ThumbnailResult.Icon(IconResolver.resolve(file))
        }

        val viewerType = detectViewerType(file.name, file.mimeType)
        if (viewerType != ViewerType.Image && viewerType != ViewerType.Pdf) {
            return ThumbnailResult.Icon(IconResolver.resolve(file))
        }

        return generateThumbnail(file)
    }

    override suspend fun generateThumbnail(file: FileItem): ThumbnailResult {
        val source = sourceProvider()
            ?: return ThumbnailResult.Icon(IconResolver.resolve(file))

        return runCatching {
            val cachedFile = source.cachePreviewFile(file)
            val localFile = file.copy(
                path = cachedFile.path,
                uri = Uri.fromFile(cachedFile).toString(),
                sourceType = SourceType.LOCAL,
            )
            localRepository.getThumbnail(localFile)
        }.getOrElse {
            ThumbnailResult.Icon(IconResolver.resolve(file))
        }
    }

    override suspend fun clearThumbnailCache() {
        localRepository.clearThumbnailCache()
    }
}
