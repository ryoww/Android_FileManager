package com.ryo.androidfilemanager.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.ryo.androidfilemanager.data.model.OpenedFile

@Composable
fun ImageViewerScreen(
    openedFile: OpenedFile,
    modifier: Modifier = Modifier,
) {
    val uri = openedFile.localUriOrNull()
    if (uri == null) {
        StreamUnsupportedPlaceholder(openedFile = openedFile, modifier = modifier)
        return
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Image preview",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
