package com.ryo.androidfilemanager.explorer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
    val context = LocalContext.current.applicationContext
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
    onNavigateUp: () -> Unit,
    onReload: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var gridMode by rememberSaveable { mutableStateOf(true) }
    var selectedFilterName by rememberSaveable { mutableStateOf(FileFilter.ALL.name) }
    val selectedFilter = FileFilter.valueOf(selectedFilterName)
    val visibleFiles = remember(uiState.files, selectedFilter) {
        uiState.files.filter { file -> file.matchesFilter(selectedFilter) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ExplorerHeader(
            rootName = uiState.rootName,
            isLoading = uiState.isLoading,
        )

        BreadcrumbCard(
            rootName = uiState.rootName,
            itemCount = visibleFiles.size,
        )

        FilterRow(
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilterName = it.name },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ViewModeButton(
                label = "Grid",
                selected = gridMode,
                onClick = { gridMode = true },
            )
            ViewModeButton(
                label = "List",
                selected = !gridMode,
                onClick = { gridMode = false },
            )
            OutlinedButton(onClick = onReload) {
                Text(text = "Reload")
            }
            OutlinedButton(
                onClick = onNavigateUp,
                enabled = uiState.canNavigateUp,
            ) {
                Text(text = "Up")
            }
        }

        if (!uiState.hasFolderPermission) {
            EmptyFolderCard(
                errorMessage = uiState.errorMessage,
                onChooseFolder = onChooseFolder,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onChooseAnotherFolder) {
                    Text(text = "Choose folder")
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
    isLoading: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Explorer",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = rootName?.let { "Local Storage / $it" }
                    ?: "Choose a local folder with Android SAF",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isLoading) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun BreadcrumbCard(
    rootName: String?,
    itemCount: Int,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Path",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = rootName ?: "No folder selected",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
            Text(
                text = "$itemCount items",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterRow(
    selectedFilter: FileFilter,
    onFilterSelected: (FileFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FileFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(text = filter.label) },
            )
        }
    }
}

@Composable
private fun FileCollection(
    gridMode: Boolean,
    files: List<FileItem>,
    thumbnailRepository: ThumbnailRepository,
    onFileClick: (FileItem) -> Unit,
) {
    if (gridMode) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = files,
                key = { it.path },
            ) { file ->
                FileGridItem(
                    file = file,
                    thumbnailRepository = thumbnailRepository,
                    onClick = { onFileClick(file) },
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = files,
                key = { it.path },
            ) { file ->
                FileListItem(
                    file = file,
                    thumbnailRepository = thumbnailRepository,
                    onClick = { onFileClick(file) },
                )
            }
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
                    text = "Select a folder through Android's system picker. The app stores persistent read access and lists files through LocalFileSource.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(onClick = onChooseFolder) {
                    Text(text = "Choose folder")
                }
            }
        }
    }
}

@Composable
private fun ViewModeButton(
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

