package com.ryo.androidfilemanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ryo.androidfilemanager.data.model.TransferKind
import com.ryo.androidfilemanager.data.model.TransferProgress
import com.ryo.androidfilemanager.explorer.formatByteSize
import kotlin.math.roundToInt

/**
 * SMB のダウンロード/アップロード進捗を、シークバー風のバーで表示する。
 * BrowserMessage と見た目のトーンを揃えている(角丸14dp・枠線・内側パディング)。
 */
@Composable
internal fun TransferProgressBar(
    progress: TransferProgress,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.62f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.56f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TransferHeaderRow(progress)
            TransferSeekBar(progress.fraction)
            TransferByteRow(progress)
        }
    }
}

@Composable
private fun TransferHeaderRow(progress: TransferProgress) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = if (progress.kind == TransferKind.DOWNLOAD) {
            Icons.Outlined.Download
        } else {
            Icons.Outlined.FileUpload
        }
        val verb = if (progress.kind == TransferKind.DOWNLOAD) "Downloading" else "Uploading"

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "$verb ${progress.fileName}",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        val countLabel = when {
            progress.totalFiles != null -> "${progress.completedFiles + 1} / ${progress.totalFiles}"
            progress.completedFiles > 0 -> "${progress.completedFiles} done"
            else -> null
        }
        if (countLabel != null) {
            Text(
                text = countLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransferSeekBar(fraction: Float?) {
    if (fraction == null) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        )
        return
    }

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(120),
        label = "transferFraction",
    )
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val fillColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
    ) {
        val trackHeight = 6.dp.toPx()
        val trackY = (size.height - trackHeight) / 2f
        val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, trackY),
            size = Size(size.width, trackHeight),
            cornerRadius = cornerRadius,
        )

        val fillWidth = size.width * animatedFraction
        if (fillWidth > 0f) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(0f, trackY),
                size = Size(fillWidth, trackHeight),
                cornerRadius = cornerRadius,
            )
        }

        val knobRadius = 6.dp.toPx()
        val knobCenterX = fillWidth.coerceIn(knobRadius, size.width - knobRadius)
        drawCircle(
            color = fillColor,
            radius = knobRadius,
            center = Offset(knobCenterX, size.height / 2f),
        )
    }
}

@Composable
private fun TransferByteRow(progress: TransferProgress) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val byteText = if (progress.totalBytes != null) {
            "${formatByteSize(progress.bytesTransferred)} / ${formatByteSize(progress.totalBytes)}"
        } else {
            formatByteSize(progress.bytesTransferred)
        }
        Text(
            text = byteText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val percent = progress.fraction?.let { (it * 100f).roundToInt() }
        if (percent != null) {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
