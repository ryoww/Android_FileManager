package com.ryo.androidfilemanager.viewer

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerControlView
import com.ryo.androidfilemanager.core.domain.OpenedFile
import com.ryo.androidfilemanager.core.domain.displayName

@OptIn(UnstableApi::class)
@Composable
fun AudioViewerScreen(
    openedFile: OpenedFile,
    modifier: Modifier = Modifier,
) {
    val mediaPlayerState = rememberMediaPlayer(openedFile)
    val error by mediaPlayerState.error

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val currentError = error
        if (currentError != null) {
            PlaybackErrorCard(
                info = currentError,
                onRetry = mediaPlayerState.retry,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .size(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxSize(),
                ) {}
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp),
                )
            }
        }

        Text(
            text = openedFile.displayName,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )

        // PlayerControlView は高さが足りないと時間表示やシークバー下段を省いた
        // 最小レイアウトになるため、フル構成が収まる高さを明示する
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.62f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                factory = { context ->
                    PlayerControlView(context).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        // 既定レイアウトが持つ黒 60% の下地は Surface 側の色に任せる
                        findViewById<android.view.View>(androidx.media3.ui.R.id.exo_controls_background)
                            ?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        findViewById<android.view.View>(androidx.media3.ui.R.id.exo_bottom_bar)
                            ?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        // 単一ファイルの再生なので前後トラックのボタンは出さない
                        setShowPreviousButton(false)
                        setShowNextButton(false)
                        player = mediaPlayerState.player
                        showTimeoutMs = 0
                        show()
                    }
                },
                update = { controlView ->
                    controlView.player = mediaPlayerState.player
                },
            )
        }
    }
}
