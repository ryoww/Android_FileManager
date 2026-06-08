package com.ryo.androidfilemanager.explorer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryo.androidfilemanager.data.local.FileManagerAccess
import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.OpenedFile
import com.ryo.androidfilemanager.data.model.ViewerType
import com.ryo.androidfilemanager.data.source.detectViewerType
import com.ryo.androidfilemanager.data.thumbnail.FileThumbnailRepository
import com.ryo.androidfilemanager.data.thumbnail.ThumbnailRepository

private enum class FileFilter(
    val label: String,
) {
    ALL("All"),
    PDF("PDF"),
    VIDEO("Video"),
    CODE("Code"),
    IMAGES("Images"),
}

@Composable
fun ExplorerScreen(
    onOpenFile: (OpenedFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentContext = LocalContext.current
    val context = currentContext.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: ExplorerViewModel = viewModel(
        factory = ExplorerViewModel.factory(context),
    )
    val thumbnailRepository = remember(context) {
        FileThumbnailRepository(context)
    }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.onFolderSelected(uri)
        }
    }
    val uiState = viewModel.uiState

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStorageAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.openedFile) {
        uiState.openedFile?.let { openedFile ->
            onOpenFile(openedFile)
            viewModel.consumeOpenedFile()
        }
    }

    ExplorerScreenContent(
        uiState = uiState,
        thumbnailRepository = thumbnailRepository,
        onChooseFolder = { folderPicker.launch(null) },
        onChooseAnotherFolder = {
            viewModel.chooseAnotherFolder()
            folderPicker.launch(null)
        },
        onRequestFullStorageAccess = {
            runCatching {
                currentContext.startActivity(FileManagerAccess.settingsIntent(context))
            }.onFailure {
                currentContext.startActivity(FileManagerAccess.appSettingsIntent(context))
            }
        },
        onNavigateUp = viewModel::navigateUp,
        onReload = viewModel::reload,
        onFileClick = viewModel::onFileSelected,
        modifier = modifier,
    )
}

@Composable
fun ExplorerScreenContent(
    uiState: ExplorerUiState,
    thumbnailRepository: ThumbnailRepository,
    onChooseFolder: () -> Unit,
    onChooseAnotherFolder: () -> Unit,
    onRequestFullStorageAccess: () -> Unit,
    onNavigateUp: () -> Unit,
    onReload: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var gridMode by rememberSaveable { mutableStateOf(true) }
    var selectedFilterName by rememberSaveable { mutableStateOf(FileFilter.ALL.name) }
    var selectedSortName by rememberSaveable { mutableStateOf(FileSortOption.DEFAULT.name) }
    val selectedFilter = FileFilter.valueOf(selectedFilterName)
    val selectedSort = FileSortOption.valueOf(selectedSortName)
    val visibleFiles = remember(uiState.files, selectedFilter, selectedSort) {
        uiState.files
            .filter { file -> file.matchesFilter(selectedFilter) }
            .sortedForDisplay(selectedSort)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExplorerHeader(
            rootName = uiState.rootName,
            storageMode = uiState.storageMode,
            canNavigateUp = uiState.canNavigateUp,
            navigateUpLabel = uiState.navigateUpLabel,
            onNavigateUp = onNavigateUp,
            isLoading = uiState.isLoading,
        )

        ExplorerToolbar(
            gridMode = gridMode,
            onGridModeChange = { gridMode = it },
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilterName = it.name },
            selectedSort = selectedSort,
            onSortSelected = { selectedSortName = it.name },
            onReload = onReload,
        )

        if (!uiState.hasFolderPermission) {
            EmptyFolderCard(
                errorMessage = uiState.errorMessage,
                statusMessage = uiState.statusMessage,
                hasFullStorageAccess = uiState.hasFullStorageAccess,
                onRequestFullStorageAccess = onRequestFullStorageAccess,
                onChooseFolder = onChooseFolder,
            )
        } else {
            if (uiState.storageMode == ExplorerStorageMode.SAF) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onChooseAnotherFolder) {
                        Text(text = "Choose SAF folder")
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            uiState.statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FileCollection(
                gridMode = gridMode,
                files = visibleFiles,
                thumbnailRepository = thumbnailRepository,
                onFileClick = onFileClick,
            )
        }
    }
}

@Composable
private fun ExplorerHeader(
    rootName: String?,
    storageMode: ExplorerStorageMode,
    canNavigateUp: Boolean,
    navigateUpLabel: String,
    onNavigateUp: () -> Unit,
    isLoading: Boolean,
) {
    val storageLabel = rootName?.let {
        if (storageMode == ExplorerStorageMode.FILE_MANAGER) {
            "Device Storage"
        } else {
            "Local Storage"
        }
    } ?: "Choose a local folder with Android SAF"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = rootName ?: "Explorer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = storageLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canNavigateUp) {
                TextButton(onClick = onNavigateUp) {
                    Text(text = "← $navigateUpLabel")
                }
            }
            if (isLoading) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun ExplorerToolbar(
    gridMode: Boolean,
    onGridModeChange: (Boolean) -> Unit,
    selectedFilter: FileFilter,
    onFilterSelected: (FileFilter) -> Unit,
    selectedSort: FileSortOption,
    onSortSelected: (FileSortOption) -> Unit,
    onReload: () -> Unit,
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
        FileFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(text = filter.label) },
            )
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
    }
}

private fun FileItem.matchesFilter(filter: FileFilter): Boolean {
    if (isDirectory) {
        return filter == FileFilter.ALL
    }

    return when (filter) {
        FileFilter.ALL -> true
        FileFilter.PDF -> detectViewerType(name, mimeType) == ViewerType.Pdf
        FileFilter.VIDEO -> detectViewerType(name, mimeType) == ViewerType.Video
        FileFilter.CODE -> detectViewerType(name, mimeType) == ViewerType.Code
        FileFilter.IMAGES -> detectViewerType(name, mimeType) == ViewerType.Image
    }
}

@Composable
private fun EmptyFolderCard(
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
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "No local folder selected",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Enable full storage access to browse Download and internal storage as a file manager. SAF folder selection remains available for privacy-scoped access.",
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
