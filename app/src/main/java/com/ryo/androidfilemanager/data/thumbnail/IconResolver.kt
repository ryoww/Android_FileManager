package com.ryo.androidfilemanager.data.thumbnail

import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.ViewerType
import com.ryo.androidfilemanager.data.source.detectViewerType

data class IconDescriptor(
    val label: String,
    val description: String,
)

object IconResolver {
    fun resolve(file: FileItem): IconDescriptor {
        if (file.isDirectory) {
            return IconDescriptor(label = "DIR", description = "Folder")
        }

        val ext = file.name.substringAfterLast('.', "").lowercase()
        val codeLabel = codeExtensionLabels[ext]
        if (codeLabel != null) {
            return IconDescriptor(label = codeLabel, description = "$codeLabel source")
        }

        return when (detectViewerType(file.name, file.mimeType)) {
            ViewerType.Pdf -> IconDescriptor(label = "PDF", description = "PDF")
            ViewerType.Image -> IconDescriptor(label = "IMG", description = "Image")
            ViewerType.Video -> IconDescriptor(label = "VID", description = "Video")
            ViewerType.Audio -> IconDescriptor(label = "AUD", description = "Audio")
            ViewerType.Text -> IconDescriptor(label = "TXT", description = "Text")
            ViewerType.Code -> IconDescriptor(label = "CODE", description = "Code")
            ViewerType.Unsupported -> IconDescriptor(label = "FILE", description = "File")
        }
    }

    private val codeExtensionLabels = mapOf(
        "kt" to "KOT",
        "java" to "JAVA",
        "py" to "PY",
        "js" to "JS",
        "ts" to "TS",
        "tsx" to "TSX",
        "jsx" to "JSX",
        "html" to "HTML",
        "css" to "CSS",
        "cpp" to "CPP",
        "c" to "C",
        "h" to "H",
        "hpp" to "HPP",
        "rs" to "RS",
        "go" to "GO",
        "php" to "PHP",
        "rb" to "RB",
        "swift" to "SWFT",
        "sql" to "SQL",
        "sh" to "SH",
    )
}

