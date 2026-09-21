package com.ryo.androidfilemanager.explorer

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryo.androidfilemanager.core.domain.FileFilter
import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.FileSortOption
import com.ryo.androidfilemanager.core.domain.OpenedFile
import com.ryo.androidfilemanager.core.domain.matchesFilter
import com.ryo.androidfilemanager.core.domain.sortedForDisplay
import com.ryo.androidfilemanager.data.local.FileManagerAccess
import com.ryo.androidfilemanager.data.thumbnail.ThumbnailRepository
import com.ryo.androidfilemanager.ui.components.BrowserEmptyState
import com.ryo.androidfilemanager.ui.components.BrowserHeader
import com.ryo.androidfilemanager.ui.components.BrowserMessage
import com.ryo.androidfilemanager.ui.components.BrowserProgressIndicator
import com.ryo.androidfilemanager.ui.components.NavigateUpAction
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExplorerScreen(
    onOpenFile: (OpenedFile) -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentContext = LocalContext.current
    val context = currentContext.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: ExplorerViewModel = koinViewModel()
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.onFolderSelected(uri)
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
        thumbnailRepository = viewModel.thumbnailRepository,
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
        onOpenMenu = onOpenMenu,
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
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val saveableStateHolder = rememberSaveableStateHolder()
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

    BackHandler(enabled = uiState.canNavigateUp) {
        onNavigateUp()
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val storageLabel = uiState.rootName?.let {
            if (uiState.storageMode == ExplorerStorageMode.FILE_MANAGER) {
                "Device Storage"
            } else {
                "Local Storage"
            }
        } ?: "Choose a local folder with Android SAF"

        val header = @Composable { headerModifier: Modifier ->
            BrowserHeader(
                title = uiState.rootName ?: "Explorer",
                subtitle = storageLabel,
                titleStyle = if (isLandscape) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                navigateUp = if (uiState.canNavigateUp) {
                    NavigateUpAction(
                        contentDescription = "Back to ${uiState.navigateUpLabel}",
                        onClick = onNavigateUp,
                    )
                } else {
                    null
                },
                onOpenMenu = if (uiState.canNavigateUp) null else onOpenMenu,
                modifier = headerModifier,
            )
        }

        val toolbar = @Composable { toolbarModifier: Modifier ->
            ExplorerToolbar(
                gridMode = gridMode,
                onGridModeChange = { gridMode = it },
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilterName = it.name },
                selectedSort = selectedSort,
                onSortSelected = { selectedSortName = it.name },
                onReload = onReload,
                modifier = toolbarModifier,
            )
        }

        if (isLandscape) {
            // 横向きは高さが限られるため、ヘッダーとツールバーを1行にまとめて縦の占有を減らす
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                header(Modifier.weight(1f))
                toolbar(Modifier)
            }
        } else {
            header(Modifier)
            toolbar(Modifier.fillMaxWidth())
        }

        BrowserProgressIndicator(visible = uiState.isLoading)

        if (!uiState.hasFolderPermission) {
            ExplorerPermissionCard(
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
                BrowserMessage(message = message, isError = true, onDismiss = null)
            }

            uiState.statusMessage?.let { message ->
                BrowserMessage(message = message, isError = false, onDismiss = null)
            }

            if (!uiState.isLoading && visibleFiles.isEmpty()) {
                if (uiState.files.isEmpty()) {
                    BrowserEmptyState(
                        title = "This folder is empty",
                        description = "No files or folders here yet.",
                    )
                } else {
                    BrowserEmptyState(
                        title = "No ${selectedFilter.label} files",
                        description = "Change the filter to All to see every item in this folder.",
                    )
                }
            } else {
                // フォルダのパスをキーに一覧のスクロール位置を保存・復元する。
                // 下の階層に入って戻ったとき、親の位置に戻れるようにするため
                saveableStateHolder.SaveableStateProvider(key = uiState.currentPath ?: "") {
                    FileCollection(
                        gridMode = gridMode,
                        files = visibleFiles,
                        thumbnailRepository = thumbnailRepository,
                        onFileClick = onFileClick,
                        isRefreshing = uiState.isLoading,
                        onRefresh = onReload,
                    )
                }
            }
        }
    }
}
