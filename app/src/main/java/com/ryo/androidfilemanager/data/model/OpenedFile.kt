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
        override val viewerType: ViewerType,
    ) : OpenedFile()
}

