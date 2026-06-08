package com.ryo.androidfilemanager.explorer

import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.thumbnail.IconResolver
import java.util.Locale

internal fun fileSubtitle(file: FileItem): String {
    if (file.isDirectory) {
        return "Folder"
    }

    val icon = IconResolver.resolve(file)
    val size = formatByteSize(file.size)
    return "${icon.description} / $size"
}

internal fun formatByteSize(bytes: Long?): String {
    if (bytes == null) {
        return "Unknown size"
    }

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

