package com.ryo.androidfilemanager.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ryo.androidfilemanager.data.model.OpenedFile

@Composable
fun VideoViewerScreen(
    openedFile: OpenedFile,
    modifier: Modifier = Modifier,
) {
    MediaPlayerView(
        openedFile = openedFile,
        modifier = modifier,
    )
}
