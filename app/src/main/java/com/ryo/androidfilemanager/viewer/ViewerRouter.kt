package com.ryo.androidfilemanager.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ryo.androidfilemanager.data.model.OpenedFile
import com.ryo.androidfilemanager.data.model.ViewerType

@Composable
fun ViewerRouter(
    openedFile: OpenedFile,
    modifier: Modifier = Modifier,
) {
    when (openedFile.viewerType) {
        ViewerType.Pdf -> PdfViewerScreen(openedFile, modifier)
        ViewerType.Image -> ImageViewerScreen(openedFile, modifier)
        ViewerType.Video -> VideoViewerScreen(openedFile, modifier)
        ViewerType.Audio -> AudioViewerScreen(openedFile, modifier)
        ViewerType.Text -> TextViewerScreen(openedFile, modifier)
        ViewerType.Code -> CodeViewerScreen(openedFile, modifier)
        ViewerType.Unsupported -> UnsupportedViewerScreen(openedFile, modifier)
    }
}

