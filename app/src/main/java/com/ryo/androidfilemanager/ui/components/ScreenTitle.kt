package com.ryo.androidfilemanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Headline used at the top of a top-level screen (Settings, SMB Connection, ...). */
@Composable
internal fun ScreenTitle(
    text: String,
    onOpenMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (onOpenMenu != null) {
        // BrowserHeader のルート表示と同じ位置・同じ間隔でハンバーガーを出す
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenMenu) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "Open navigation menu",
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            modifier = modifier,
        )
    }
}
