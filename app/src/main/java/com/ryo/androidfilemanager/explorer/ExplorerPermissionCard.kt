package com.ryo.androidfilemanager.explorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ExplorerPermissionCard(
    errorMessage: String?,
    statusMessage: String?,
    hasFullStorageAccess: Boolean,
    onRequestFullStorageAccess: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Choose a folder",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Allow full storage access, or pick a folder with the system picker.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                statusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = onRequestFullStorageAccess,
                    enabled = !hasFullStorageAccess,
                ) {
                    Text(text = "Enable full storage access")
                }
                OutlinedButton(onClick = onChooseFolder) {
                    Text(text = "Choose SAF folder")
                }
            }
        }
    }
}
