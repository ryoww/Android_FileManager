package com.ryo.androidfilemanager.viewer

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.ryo.androidfilemanager.data.model.OpenedFile
import kotlinx.coroutines.delay

// タップ範囲の判定に使う左右のしきい値（PlayerView.md の指示どおり左右 40% / 中央 20%）
private const val SEEK_ZONE_RATIO = 0.4f

@OptIn(UnstableApi::class)
@Composable
internal fun MediaPlayerView(
    openedFile: OpenedFile,
    modifier: Modifier = Modifier,
) {
    val mediaPlayerState = rememberMediaPlayer(openedFile)
    val player = mediaPlayerState.player
    val error by mediaPlayerState.error

    var playerView by remember(openedFile) { mutableStateOf<PlayerView?>(null) }
    var controllerFullyVisible by remember(openedFile) { mutableStateOf(true) }
    var seekHint by remember(openedFile) { mutableStateOf<SeekHint?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    controllerHideOnTouch = false
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controllerFullyVisible = visibility == android.view.View.VISIBLE
                        },
                    )
                    player.addListener(
                        object : Player.Listener {
                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                keepScreenOn = isPlaying
                            }
                        },
                    )
                    playerView = this
                }
            },
            update = { view ->
                view.player = player
            },
        )

        // コントローラが出ている間は下部のシークバー・ボタンへのタップを奪わないよう、
        // その分の高さを除いた領域だけをタップ検出の対象にする
        val bottomExclusionHeight = if (controllerFullyVisible) 96.dp else 0.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomExclusionHeight)
                .pointerInput(playerView) {
                    detectTapGestures(
                        onTap = {
                            val view = playerView ?: return@detectTapGestures
                            if (view.isControllerFullyVisible) view.hideController() else view.showController()
                        },
                        onDoubleTap = { offset: Offset ->
                            handleDoubleTap(
                                offsetX = offset.x,
                                widthPx = size.width.toFloat(),
                                player = player,
                                onSeekHint = { seekHint = it },
                            )
                        },
                    )
                },
        )

        SeekHintOverlay(
            hint = seekHint,
            onExpire = { seekHint = null },
            modifier = Modifier.fillMaxSize(),
        )

        error?.let {
            PlaybackErrorCard(
                info = it,
                onRetry = mediaPlayerState.retry,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private enum class SeekDirection { BACK, FORWARD }
private data class SeekHint(val direction: SeekDirection)

private fun handleDoubleTap(
    offsetX: Float,
    widthPx: Float,
    player: Player,
    onSeekHint: (SeekHint) -> Unit,
) {
    val leftBoundary = widthPx * SEEK_ZONE_RATIO
    val rightBoundary = widthPx * (1f - SEEK_ZONE_RATIO)
    when {
        offsetX < leftBoundary -> {
            player.seekBack()
            onSeekHint(SeekHint(SeekDirection.BACK))
        }
        offsetX > rightBoundary -> {
            player.seekForward()
            onSeekHint(SeekHint(SeekDirection.FORWARD))
        }
        else -> {
            if (player.isPlaying) player.pause() else player.play()
        }
    }
}

@Composable
private fun SeekHintOverlay(
    hint: SeekHint?,
    onExpire: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hint == null) return
    var visible by remember(hint) { mutableStateOf(true) }
    LaunchedEffect(hint) {
        delay(700)
        visible = false
        onExpire()
    }
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(
                if (hint.direction == SeekDirection.BACK) Alignment.CenterStart else Alignment.CenterEnd,
            ),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                Text(
                    text = if (hint.direction == SeekDirection.BACK) "-10s" else "+10s",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
internal fun PlaybackErrorCard(
    info: PlaybackErrorInfo,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.padding(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = info.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = info.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors()) {
                Text("Retry")
            }
        }
    }
}
