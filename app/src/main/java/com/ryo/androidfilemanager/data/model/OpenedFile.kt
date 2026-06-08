package com.ryo.androidfilemanager.data.model

import android.net.Uri
import com.ryo.androidfilemanager.data.smb.RemoteReadableFile

sealed class OpenedFile {
    abstract val viewerType: ViewerType

    data class Local(
        val uri: Uri,
        override val viewerType: ViewerType,
    ) : OpenedFile()

    data class Stream(
        val remoteFile: RemoteReadableFile,
        val name: String? = null,
        val mimeType: String? = null,
        override val viewerType: ViewerType,
    ) : OpenedFile()
}
