package com.ryo.androidfilemanager.explorer

import com.ryo.androidfilemanager.core.domain.ViewerType
import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.detectViewerType

const val A4_PORTRAIT_ASPECT_RATIO = 210f / 297f

fun thumbnailContentAspectRatio(file: FileItem): Float? {
    if (file.isDirectory) {
        return null
    }
    return thumbnailContentAspectRatio(detectViewerType(file.name, file.mimeType))
}

fun thumbnailContentAspectRatio(viewerType: ViewerType): Float? = when (viewerType) {
    ViewerType.Pdf -> A4_PORTRAIT_ASPECT_RATIO
    else -> null
}
