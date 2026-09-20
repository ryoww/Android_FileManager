package com.ryo.androidfilemanager.viewer

import android.net.Uri
import com.ryo.androidfilemanager.core.domain.OpenedFile

internal fun OpenedFile.localUriOrNull(): Uri? = when (this) {
    is OpenedFile.Local -> Uri.parse(uri)
    is OpenedFile.Stream -> null
}

