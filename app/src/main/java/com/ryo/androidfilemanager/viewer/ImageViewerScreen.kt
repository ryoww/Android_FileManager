package com.ryo.androidfilemanager.viewer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import coil3.compose.AsyncImage
import com.ryo.androidfilemanager.core.domain.OpenedFile

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

    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var containerSize by remember(uri) { mutableStateOf(Size.Zero) }

    fun coerceOffset(candidate: Offset, currentScale: Float): Offset {
        if (currentScale <= 1f || containerSize == Size.Zero) {
            return Offset.Zero
        }
        val maxX = (containerSize.width * (currentScale - 1)) / 2
        val maxY = (containerSize.height * (currentScale - 1)) / 2
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        offset = if (newScale > 1f) {
            coerceOffset(offset + panChange, newScale)
        } else {
            Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size -> containerSize = Size(size.width.toFloat(), size.height.toFloat()) }
            .transformable(transformState)
            .pointerInput(uri) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val targetScale = 2.5f
                            val centerX = containerSize.width / 2
                            val centerY = containerSize.height / 2
                            val newOffset = Offset(
                                x = (centerX - tapOffset.x) * (targetScale - 1),
                                y = (centerY - tapOffset.y) * (targetScale - 1),
                            )
                            scale = targetScale
                            offset = coerceOffset(newOffset, targetScale)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Image preview",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit,
        )
    }
}
