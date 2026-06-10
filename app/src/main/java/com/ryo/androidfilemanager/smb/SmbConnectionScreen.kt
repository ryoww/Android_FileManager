package com.ryo.androidfilemanager.smb

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryo.androidfilemanager.data.model.OpenedFile
import com.ryo.androidfilemanager.explorer.FileCollection
import com.ryo.androidfilemanager.explorer.FileSortOption
import com.ryo.androidfilemanager.explorer.sortedForDisplay

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
    var gridMode by rememberSaveable { mutableStateOf(true) }
    var selectedSortName by rememberSaveable { mutableStateOf(FileSortOption.DEFAULT.name) }
    val selectedSort = FileSortOption.valueOf(selectedSortName)
    val visibleFiles = remember(uiState.files, selectedSort) {
        uiState.files.sortedForDisplay(selectedSort)
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!uiState.connected || uiState.connectionFormExpanded) {
            Text(
                text = "SMB Connection",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        if (uiState.connected && !uiState.connectionFormExpanded) {
            ConnectedSummary(
                uiState = uiState,
                onEdit = viewModel::editConnection,
                onDisconnect = viewModel::disconnect,
            )
        } else {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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

        if (!uiState.connected || uiState.connectionFormExpanded || uiState.isLoading) {
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
                if (uiState.isLoading) {
                    CircularProgressIndicator()
                }
            }
        }

        if (uiState.connected) {
            SmbBrowserHeader(
                uiState = uiState,
                onNavigateParent = viewModel::navigateUp,
            )
            SmbBrowserToolbar(
                gridMode = gridMode,
                onGridModeChange = { gridMode = it },
                selectedSort = selectedSort,
                onSortSelected = { selectedSortName = it.name },
                selectedCount = uiState.selectedCount,
                isDownloading = uiState.isDownloading,
                onReload = viewModel::reload,
                onDownloadSelected = viewModel::downloadSelectedFiles,
                onClearSelection = viewModel::clearSelection,
            )
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

        FileCollection(
            gridMode = gridMode,
            files = visibleFiles,
            thumbnailRepository = viewModel.thumbnailRepository,
            selectedPaths = uiState.selectedPaths,
            onFileClick = viewModel::onFileSelected,
            onFileLongClick = viewModel::onFileLongPressed,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SmbBrowserHeader(
    uiState: SmbExplorerUiState,
    onNavigateParent: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = uiState.currentPath
                    .takeIf { it.isNotBlank() }
                    ?.substringAfterLast('/')
                    ?: "Share Root",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "SMB / ${uiState.currentPath.ifBlank { "/" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (uiState.canNavigateUp) {
            TextButton(onClick = onNavigateParent) {
                Text(text = "← ${uiState.parentLabel()}")
            }
        }
    }
}

@Composable
private fun SmbBrowserToolbar(
    gridMode: Boolean,
    onGridModeChange: (Boolean) -> Unit,
    selectedSort: FileSortOption,
    onSortSelected: (FileSortOption) -> Unit,
    selectedCount: Int,
    isDownloading: Boolean,
    onReload: () -> Unit,
    onDownloadSelected: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 48.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactModeButton(
            label = "Grid",
            selected = gridMode,
            onClick = { onGridModeChange(true) },
        )
        CompactModeButton(
            label = "List",
            selected = !gridMode,
            onClick = { onGridModeChange(false) },
        )
        OutlinedButton(onClick = onReload) {
            Text(text = "Reload")
        }
        Text(
            text = "Sort",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FileSortOption.entries.forEach { sortOption ->
            FilterChip(
                selected = selectedSort == sortOption,
                onClick = { onSortSelected(sortOption) },
                label = { Text(text = sortOption.label) },
            )
        }
        if (selectedCount > 0) {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Button(
                onClick = onDownloadSelected,
                enabled = !isDownloading,
            ) {
                Text(text = "Download")
            }
            OutlinedButton(
                onClick = onClearSelection,
                enabled = !isDownloading,
            ) {
                Text(text = "Clear")
            }
        }
    }
}

@Composable
private fun CompactModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(text = label)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(text = label)
        }
    }
}

private fun SmbExplorerUiState.parentLabel(): String =
    if (pathStack.size <= 2) {
        "Share Root"
    } else {
        "Parent Folder"
    }

@Composable
private fun ConnectedSummary(
    uiState: SmbExplorerUiState,
    onEdit: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(Color(0xFF4CE68A), CircleShape),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = "${uiState.host} / ${uiState.shareName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = listOfNotNull(
                        uiState.username.takeIf { it.isNotBlank() }?.let { "User $it" },
                        uiState.currentPath.takeIf { it.isNotBlank() }?.let { "/$it" },
                    ).ifEmpty { listOf("Connected") }.joinToString("  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = "Edit",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Text(
                text = "Disconnect",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable(onClick = onDisconnect)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
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
