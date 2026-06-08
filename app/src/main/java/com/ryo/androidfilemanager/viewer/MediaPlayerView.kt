package com.ryo.androidfilemanager.viewer

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.ryo.androidfilemanager.data.model.OpenedFile
import com.ryo.androidfilemanager.data.smb.SmbDataSource

@OptIn(UnstableApi::class)
@Composable
internal fun MediaPlayerView(
    openedFile: OpenedFile,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(openedFile) {
        ExoPlayer.Builder(context).build().apply {
            when (openedFile) {
                is OpenedFile.Local -> setMediaItem(MediaItem.fromUri(openedFile.uri))
                is OpenedFile.Stream -> {
                    val mediaItem = MediaItem.fromUri("smb://stream/${openedFile.remoteFile.hashCode()}")
                    val mediaSource = ProgressiveMediaSource.Factory(
                        SmbDataSource.Factory(openedFile.remoteFile),
                    ).createMediaSource(mediaItem)
                    setMediaSource(mediaSource)
                }
            }
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
            }
        },
        update = { playerView ->
            playerView.player = player
        },
    )
}
