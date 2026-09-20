package com.ryo.androidfilemanager.smb

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ryo.androidfilemanager.ui.components.BrowseIconButton

@Composable
internal fun ConnectedSummary(
    uiState: SmbExplorerUiState,
    onEdit: () -> Unit,
    onDisconnect: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        headlineContent = {
            Text(
                text = "${uiState.form.host} / ${uiState.form.shareName}",
                maxLines = 1,
            )
        },
        supportingContent = { Text(text = "Connected") },
        trailingContent = {
            Row {
                BrowseIconButton(
                    icon = Icons.Outlined.Edit,
                    contentDescription = "Edit SMB connection",
                    onClick = onEdit,
                )
                BrowseIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = "Disconnect SMB connection",
                    onClick = onDisconnect,
                )
            }
        },
    )
}
