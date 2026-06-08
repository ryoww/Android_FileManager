package com.ryo.androidfilemanager.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ryo.androidfilemanager.data.model.OpenedFile

@Composable
internal fun StreamUnsupportedPlaceholder(
    openedFile: OpenedFile,
    modifier: Modifier = Modifier,
) {
    ViewerPlaceholder(
        title = "SMB Stream Pending",
        description = "This file was routed as a stream, but this viewer expects a local URI. Video and audio streams are handled through SmbDataSource.",
        openedFile = openedFile,
        modifier = modifier,
    )
}
