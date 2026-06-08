package com.ryo.androidfilemanager.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ryo.androidfilemanager.data.model.OpenedFile

@Composable
fun CodeViewerScreen(
    openedFile: OpenedFile,
    modifier: Modifier = Modifier,
) {
    TextFileContent(
        openedFile = openedFile,
        monospace = true,
        modifier = modifier,
    )
}
