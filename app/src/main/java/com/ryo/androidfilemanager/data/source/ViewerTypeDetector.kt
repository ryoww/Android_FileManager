package com.ryo.androidfilemanager.data.source

import com.ryo.androidfilemanager.data.model.ViewerType

fun detectViewerType(name: String, mimeType: String?): ViewerType {
    val ext = name.substringAfterLast('.', "").lowercase()

    return when {
        mimeType?.startsWith("image/") == true -> ViewerType.Image
        mimeType == "application/pdf" || ext == "pdf" -> ViewerType.Pdf
        mimeType?.startsWith("video/") == true -> ViewerType.Video
        mimeType?.startsWith("audio/") == true -> ViewerType.Audio

        ext in setOf(
            "txt",
            "md",
            "json",
            "csv",
            "log",
            "xml",
            "yaml",
            "yml",
        ) -> ViewerType.Text

        ext in setOf(
            "kt",
            "java",
            "py",
            "js",
            "ts",
            "tsx",
            "jsx",
            "html",
            "css",
            "cpp",
            "c",
            "h",
            "hpp",
            "rs",
            "go",
            "php",
            "rb",
            "swift",
            "sql",
            "sh",
        ) -> ViewerType.Code

        else -> ViewerType.Unsupported
    }
}

