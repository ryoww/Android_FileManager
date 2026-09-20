package com.ryo.androidfilemanager.viewer

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.ryo.androidfilemanager.core.domain.OpenedFile
import com.ryo.androidfilemanager.data.smb.SmbDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** ビューワーが握るプレイヤーの状態一式。エラーと再試行を UI 側に公開する。 */
internal class MediaPlayerState(
    val player: ExoPlayer,
    val error: State<PlaybackErrorInfo?>,
    val retry: () -> Unit,
)

internal data class PlaybackErrorInfo(val title: String, val detail: String)

// OpenedFile ごとに再生位置を rememberSaveable で引くためのキー
private fun OpenedFile.savedPositionKey(): String = when (this) {
    is OpenedFile.Local -> uri
    is OpenedFile.Stream -> "stream:${name}"
}

private fun classifyError(error: PlaybackException, openedFile: OpenedFile): PlaybackErrorInfo {
    val code = error.errorCode
    return when {
        code == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            code == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
            code == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
            PlaybackErrorInfo(
                title = "Codec not supported",
                detail = "This device cannot decode this media format. Try another app or re-encode the file.",
            )
        code in 2000..2999 -> PlaybackErrorInfo(
            title = "Read error",
            detail = when (openedFile) {
                is OpenedFile.Stream -> "The SMB stream was interrupted. Check the connection and retry."
                is OpenedFile.Local -> "The file could not be read. It may have been moved or the permission expired."
            },
        )
        code in 3000..3999 -> PlaybackErrorInfo(
            title = "Unreadable media",
            detail = "The file is damaged or is not a supported container.",
        )
        else -> PlaybackErrorInfo(
            title = "Playback failed",
            detail = error.message ?: "Unknown playback error (code ${error.errorCode}).",
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun rememberMediaPlayer(openedFile: OpenedFile): MediaPlayerState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val positionKey = openedFile.savedPositionKey()

    var savedPosition by rememberSaveable(positionKey) { mutableStateOf(0L) }
    var savedPlayWhenReady by rememberSaveable(positionKey) { mutableStateOf(true) }
    val errorState = remember(openedFile) { mutableStateOf<PlaybackErrorInfo?>(null) }
    var error by errorState

    // release() 直前に読む最新値を保つため、DisposableEffect のクロージャ内で古い値を掴まないようにする
    val latestOpenedFile by rememberUpdatedState(openedFile)

    val player = remember(openedFile) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                addListener(
                    object : Player.Listener {
                        override fun onPlayerError(playbackException: PlaybackException) {
                            error = classifyError(playbackException, latestOpenedFile)
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
                seekTo(savedPosition)
                playWhenReady = savedPlayWhenReady
                prepare()
            }
    }

    DisposableEffect(lifecycleOwner, player) {
        var wasPlaying = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    wasPlaying = player.isPlaying
                    player.pause()
                }
                Lifecycle.Event.ON_START -> {
                    if (wasPlaying) player.play()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(player) {
        onDispose {
            savedPosition = player.currentPosition
            savedPlayWhenReady = player.playWhenReady
            player.release()
            if (latestOpenedFile is OpenedFile.Stream) {
                val remoteFile = (latestOpenedFile as OpenedFile.Stream).remoteFile
                runBlocking(Dispatchers.IO) {
                    remoteFile.close()
                }
            }
        }
    }

    return MediaPlayerState(
        player = player,
        error = errorState,
        retry = {
            error = null
            player.prepare()
            player.play()
        },
    )
}
