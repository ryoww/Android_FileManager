package com.ryo.androidfilemanager.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        content = { Column(content = content) },
    )
}

/** 操作ボタンのない情報行。SettingsActionRow と同じ余白・面色にそろえる */
@Composable
internal fun SettingsInfoRow(
    title: String,
    value: String,
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = value) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun SettingsActionRow(
    title: String,
    value: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = value) },
        trailingContent = {
            TextButton(onClick = onClick) {
                Text(text = actionLabel)
            }
        },
        // Card の面色をそのまま透けさせ、ListItem 独自の面色と二重にならないようにする
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
