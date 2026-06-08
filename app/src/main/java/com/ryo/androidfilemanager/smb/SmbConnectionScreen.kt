package com.ryo.androidfilemanager.smb

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryo.androidfilemanager.data.model.OpenedFile
import com.ryo.androidfilemanager.data.thumbnail.SmbThumbnailRepository
import com.ryo.androidfilemanager.explorer.FileListItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmbConnectionScreen(
    onOpenFile: (OpenedFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val viewModel: SmbExplorerViewModel = viewModel(
        factory = SmbExplorerViewModel.factory(context),
    )
    val uiState = viewModel.uiState
    val thumbnailRepository = remember(context, viewModel) {
        SmbThumbnailRepository(context) {
            viewModel.currentSource()
        }
    }

    LaunchedEffect(uiState.openedFile) {
        uiState.openedFile?.let { openedFile ->
            onOpenFile(openedFile)
            viewModel.consumeOpenedFile()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "SMB Connection",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (uiState.connected && !uiState.connectionFormExpanded) {
                    ConnectedSummary(
                        uiState = uiState,
                        onEdit = viewModel::editConnection,
                        onDisconnect = viewModel::disconnect,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (uiState.hasSavedConnection) "Saved connection" else "Connect to a share",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (uiState.connected) {
                            OutlinedButton(onClick = viewModel::hideConnectionForm) {
                                Text(text = "Done")
                            }
                        }
                    }
                    ConnectionFields(
                        uiState = uiState,
                        viewModel = viewModel,
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = viewModel::testConnection) {
                Text(text = "Test")
            }
            Button(onClick = viewModel::connectAndListRoot) {
                Text(text = if (uiState.connected) "Reconnect" else "Connect")
            }
            if (uiState.hasSavedConnection && !uiState.connected) {
                OutlinedButton(onClick = viewModel::clearSavedConnection) {
                    Text(text = "Clear saved")
                }
            }
            OutlinedButton(
                onClick = viewModel::navigateUp,
                enabled = uiState.canNavigateUp,
            ) {
                Text(text = "Up")
            }
            OutlinedButton(
                onClick = viewModel::reload,
                enabled = uiState.connected,
            ) {
                Text(text = "Reload")
            }
            if (uiState.isLoading) {
                CircularProgressIndicator()
            }
        }

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        uiState.statusMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = uiState.files,
                key = { it.path },
            ) { file ->
                FileListItem(
                    file = file,
                    thumbnailRepository = thumbnailRepository,
                    onClick = { viewModel.onFileSelected(file) },
                )
            }
        }
    }
}

@Composable
private fun ConnectedSummary(
    uiState: SmbExplorerUiState,
    onEdit: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${uiState.host} / ${uiState.shareName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = listOfNotNull(
                        uiState.username.takeIf { it.isNotBlank() }?.let { "User: $it" },
                        uiState.currentPath.takeIf { it.isNotBlank() }?.let { "Path: /$it" },
                    ).ifEmpty { listOf("Connected") }.joinToString("  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onEdit) {
                Text(text = "Edit")
            }
            OutlinedButton(onClick = onDisconnect) {
                Text(text = "Disconnect")
            }
        }
    }
}

@Composable
private fun ConnectionFields(
    uiState: SmbExplorerUiState,
    viewModel: SmbExplorerViewModel,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = uiState.host,
                onValueChange = viewModel::updateHost,
                label = { Text(text = "Host") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = uiState.port,
                onValueChange = viewModel::updatePort,
                label = { Text(text = "Port") },
                modifier = Modifier.weight(0.45f),
            )
        }
        OutlinedTextField(
            value = uiState.shareName,
            onValueChange = viewModel::updateShareName,
            label = { Text(text = "Share name") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::updateUsername,
                label = { Text(text = "Username") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = uiState.domain,
                onValueChange = viewModel::updateDomain,
                label = { Text(text = "Domain") },
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::updatePassword,
            label = { Text(text = "Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
