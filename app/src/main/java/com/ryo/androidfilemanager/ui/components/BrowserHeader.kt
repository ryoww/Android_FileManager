package com.ryo.androidfilemanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** A back-navigation action shown at the leading edge of a [BrowserHeader]. */
internal data class NavigateUpAction(
    val contentDescription: String,
    val onClick: () -> Unit,
)

/**
 * Shared "title + subtitle (+ optional back button)" header used by the
 * Explorer and SMB browser screens.
 */
@Composable
internal fun BrowserHeader(
    title: String,
    subtitle: String,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    navigateUp: NavigateUpAction? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigateUp != null) {
            // よくある「戻る」の形: 見出しの先頭にアイコンだけの丸ボタンを置く。
            // label はここでは表示せず contentDescription にのみ使う
            IconButton(onClick = navigateUp.onClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = navigateUp.contentDescription,
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = titleStyle,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
