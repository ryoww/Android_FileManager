package com.ryo.androidfilemanager.explorer

import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.ViewerType
import com.ryo.androidfilemanager.data.source.detectViewerType

internal enum class FileFilter(
    val label: String,
) {
    ALL("All"),
    PDF("PDF"),
    VIDEO("Video"),
    CODE("Code"),
    IMAGES("Images"),
}

internal fun FileItem.matchesFilter(filter: FileFilter): Boolean {
    if (isDirectory) {
        return filter == FileFilter.ALL
    }

    return when (filter) {
        FileFilter.ALL -> true
        FileFilter.PDF -> detectViewerType(name, mimeType) == ViewerType.Pdf
        FileFilter.VIDEO -> detectViewerType(name, mimeType) == ViewerType.Video
        FileFilter.CODE -> detectViewerType(name, mimeType) == ViewerType.Code
        FileFilter.IMAGES -> detectViewerType(name, mimeType) == ViewerType.Image
    }
}
