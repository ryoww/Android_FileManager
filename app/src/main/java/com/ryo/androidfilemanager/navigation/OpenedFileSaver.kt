package com.ryo.androidfilemanager.navigation

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.ryo.androidfilemanager.core.domain.OpenedFile
import com.ryo.androidfilemanager.core.domain.ViewerType

// OpenedFile.Stream は生きた SMB 接続を握っており復元できないため null として保存する
// （プロセス再生成後は一覧に戻る）。Local はコンストラクタ引数を文字列 3 つに分解して保存する。
internal val OpenedFileSaver: Saver<OpenedFile?, Any> = listSaver(
    save = { value ->
        when (value) {
            is OpenedFile.Local -> listOf(
                value.uri,
                value.viewerType.displayName,
                value.name,
            )
            is OpenedFile.Stream -> emptyList()
            null -> emptyList()
        }
    },
    restore = { saved ->
        if (saved.size < 2) {
            null
        } else {
            val uri = saved[0] as String
            val viewerType = viewerTypeFromDisplayName(saved[1] as String)
            val name = saved.getOrNull(2)
            OpenedFile.Local(uri = uri, viewerType = viewerType, name = name)
        }
    },
)

private fun viewerTypeFromDisplayName(displayName: String): ViewerType = when (displayName) {
    ViewerType.Pdf.displayName -> ViewerType.Pdf
    ViewerType.Image.displayName -> ViewerType.Image
    ViewerType.Video.displayName -> ViewerType.Video
    ViewerType.Audio.displayName -> ViewerType.Audio
    ViewerType.Text.displayName -> ViewerType.Text
    ViewerType.Code.displayName -> ViewerType.Code
    else -> ViewerType.Unsupported
}
