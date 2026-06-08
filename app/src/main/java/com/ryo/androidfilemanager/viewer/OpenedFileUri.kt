package com.ryo.androidfilemanager.viewer

import android.net.Uri
import com.ryo.androidfilemanager.data.model.OpenedFile

internal fun OpenedFile.localUriOrNull(): Uri? = when (this) {
    is OpenedFile.Local -> uri
    is OpenedFile.Stream -> null
}

