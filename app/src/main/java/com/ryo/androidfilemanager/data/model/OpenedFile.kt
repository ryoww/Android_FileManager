package com.ryo.androidfilemanager.data.model

import android.net.Uri
import com.ryo.androidfilemanager.data.smb.RemoteReadableFile

sealed class OpenedFile {
    abstract val viewerType: ViewerType
    abstract val name: String?

    data class Local(
        val uri: Uri,
        override val viewerType: ViewerType,
        override val name: String? = null,
    ) : OpenedFile()

    data class Stream(
        val remoteFile: RemoteReadableFile,
        override val name: String? = null,
        val mimeType: String? = null,
        override val viewerType: ViewerType,
    ) : OpenedFile()
}

// ヘッダー表示用: 実ファイル名が無ければビューワー種別名にフォールバックする
val OpenedFile.displayName: String
    get() = name?.takeIf { it.isNotBlank() } ?: viewerType.displayName

// FileSource が名前を埋めていない場合に FileItem.name で補完する（ViewModel から呼ぶ）
fun OpenedFile.withNameFallback(fallback: String): OpenedFile = when (this) {
    is OpenedFile.Local -> copy(name = name ?: fallback)
    is OpenedFile.Stream -> copy(name = name ?: fallback)
}
