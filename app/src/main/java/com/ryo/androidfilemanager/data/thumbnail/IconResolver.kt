package com.ryo.androidfilemanager.data.thumbnail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.ui.graphics.vector.ImageVector
import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.ViewerType
import com.ryo.androidfilemanager.core.domain.detectViewerType

// label は一覧のグリッド表示で使っていた短縮ラベルだったが呼び出し元が無くなったため削除。
// description は fileSubtitle（例:「PDF / 1.2 MB」）で表示に使うため残す
data class IconDescriptor(
    val description: String,
    val icon: ImageVector,
)

object IconResolver {
    fun resolve(file: FileItem): IconDescriptor {
        if (file.isDirectory) {
            return IconDescriptor(description = "Folder", icon = Icons.Outlined.Folder)
        }

        val ext = file.name.substringAfterLast('.', "").lowercase()
        val codeLabel = codeExtensionLabels[ext]
        if (codeLabel != null) {
            return IconDescriptor(description = "$codeLabel source", icon = Icons.Outlined.Code)
        }

        return when (detectViewerType(file.name, file.mimeType)) {
            ViewerType.Pdf -> IconDescriptor(description = "PDF", icon = Icons.Outlined.PictureAsPdf)
            ViewerType.Image -> IconDescriptor(description = "Image", icon = Icons.Outlined.Image)
            ViewerType.Video -> IconDescriptor(description = "Video", icon = Icons.Outlined.Movie)
            ViewerType.Audio -> IconDescriptor(description = "Audio", icon = Icons.Outlined.AudioFile)
            ViewerType.Text -> IconDescriptor(description = "Text", icon = Icons.Outlined.Description)
            ViewerType.Code -> IconDescriptor(description = "Code", icon = Icons.Outlined.Code)
            ViewerType.Unsupported -> IconDescriptor(description = "File", icon = Icons.AutoMirrored.Outlined.InsertDriveFile)
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
