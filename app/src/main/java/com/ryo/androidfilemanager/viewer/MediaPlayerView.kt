package com.ryo.androidfilemanager.viewer

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
    var playbackError by remember(openedFile) { mutableStateOf<String?>(null) }
    var playbackState by remember(openedFile) { mutableIntStateOf(Player.STATE_IDLE) }
    var isLoading by remember(openedFile) { mutableStateOf(false) }
    val player = remember(openedFile) {
        ExoPlayer.Builder(context).build().apply {
            addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackStateValue: Int) {
                        playbackState = playbackStateValue
                    }

                    override fun onIsLoadingChanged(isLoadingValue: Boolean) {
                        isLoading = isLoadingValue
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        playbackError = error.message ?: "Media playback failed."
                    }
                },
            )
            when (openedFile) {
                is OpenedFile.Local -> setMediaItem(MediaItem.fromUri(openedFile.uri))
                is OpenedFile.Stream -> {
                    val encodedName = android.net.Uri.encode(openedFile.name ?: "stream")
                    val mediaItem = MediaItem.Builder()
                        .setUri("smb://stream/$encodedName")
                        .apply {
                            openedFile.mimeType?.let { setMimeType(it) }
                        }
                        .build()
                    val mediaSource = ProgressiveMediaSource.Factory(
                        SmbDataSource.Factory(openedFile.remoteFile),
                    ).createMediaSource(mediaItem)
                    setMediaSource(mediaSource)
                }
            }
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
            if (openedFile is OpenedFile.Stream) {
                kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    openedFile.remoteFile.close()
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                }
            },
            update = { playerView ->
                playerView.player = player
                playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                playerView.showController()
            },
        )
        if (openedFile is OpenedFile.Stream) {
            PlaybackStatusOverlay(
                state = playbackState,
                isLoading = isLoading,
                error = playbackError,
                streamSize = openedFile.remoteFile.size,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun PlaybackStatusOverlay(
    state: Int,
    isLoading: Boolean,
    error: String?,
    streamSize: Long,
    modifier: Modifier = Modifier,
) {
    val stateLabel = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN"
    }
    val message = error ?: "SMB stream: $stateLabel / loading=$isLoading / size=$streamSize"

    Text(
        text = message,
        color = if (error == null) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.error
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}
