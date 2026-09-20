package com.ryo.androidfilemanager.smb

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryo.androidfilemanager.data.model.OpenedFile
import com.ryo.androidfilemanager.explorer.FileSortOption
import com.ryo.androidfilemanager.explorer.sortedForDisplay
import com.ryo.androidfilemanager.ui.components.BrowserEmptyState
import com.ryo.androidfilemanager.ui.components.BrowserHeader
import com.ryo.androidfilemanager.ui.components.BrowserMessage
import com.ryo.androidfilemanager.ui.components.BrowserProgressIndicator
import com.ryo.androidfilemanager.ui.components.NavigateUpAction
import com.ryo.androidfilemanager.ui.components.ScreenTitle
import com.ryo.androidfilemanager.ui.components.TransferProgressBar
import com.ryo.androidfilemanager.explorer.FileCollection

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
    val uploadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        viewModel.uploadFiles(uris)
    }
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

    BackHandler(enabled = uiState.connected && uiState.canNavigateUp) {
        viewModel.navigateUp()
    }
    BackHandler(enabled = uiState.connected && uiState.connectionFormExpanded) {
        viewModel.hideConnectionForm()
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // フォーム表示中（未接続 or 編集中）は横向きだと一覧と両立できる高さがないため、
    // フォームだけをスクロール可能な領域に表示し、一覧・サマリーは隠す
    val showingForm = !uiState.connected || uiState.connectionFormExpanded

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showingForm) {
            ScreenTitle(text = "SMB Connection")

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SmbConnectionForm(
                    uiState = uiState,
                    onHostChange = viewModel::updateHost,
                    onPortChange = viewModel::updatePort,
                    onShareNameChange = viewModel::updateShareName,
                    onUsernameChange = viewModel::updateUsername,
                    onDomainChange = viewModel::updateDomain,
                    onPasswordChange = viewModel::updatePassword,
                    onTest = viewModel::testConnection,
                    onConnect = viewModel::connectAndListRoot,
                    onClearSaved = viewModel::clearSavedConnection,
                    onDone = viewModel::hideConnectionForm,
                )

                uiState.errorMessage?.let { message ->
                    BrowserMessage(message = message, isError = true, onDismiss = null)
                }

                uiState.statusMessage?.let { message ->
                    BrowserMessage(message = message, isError = false, onDismiss = null)
                }
            }
        } else {
            ConnectedSummary(
                uiState = uiState,
                onEdit = viewModel::editConnection,
                onDisconnect = viewModel::disconnect,
            )

            val header = @Composable { headerModifier: Modifier ->
                BrowserHeader(
                    title = uiState.currentPath
                        .takeIf { it.isNotBlank() }
                        ?.substringAfterLast('/')
                        ?: "Share Root",
                    subtitle = "SMB / ${uiState.currentPath.ifBlank { "/" }}",
                    titleStyle = MaterialTheme.typography.titleMedium,
                    navigateUp = if (uiState.canNavigateUp) {
                        NavigateUpAction(
                            label = "Back",
                            contentDescription = "Back to ${uiState.parentLabel()}",
                            onClick = viewModel::navigateUp,
                        )
                    } else {
                        null
                    },
                    modifier = headerModifier,
                )
            }

            val toolbar = @Composable { toolbarModifier: Modifier ->
                SmbBrowserToolbar(
                    gridMode = gridMode,
                    onGridModeChange = { gridMode = it },
                    selectedSort = selectedSort,
                    onSortSelected = { selectedSortName = it.name },
                    selectedCount = uiState.selectedCount,
                    isDownloading = uiState.isDownloading,
                    isUploading = uiState.isUploading,
                    onReload = viewModel::reload,
                    onUpload = { uploadPicker.launch(arrayOf("*/*")) },
                    onDownloadSelected = viewModel::downloadSelectedFiles,
                    onClearSelection = viewModel::clearSelection,
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

            // ファイルの取得・保存・アップロード中はバイト数付きの進捗バーに切り替える。
            // 一覧取得のような総量が分からない待ちは従来の細いバーのまま
            val transferProgress = uiState.transferProgress
            if (transferProgress != null) {
                TransferProgressBar(progress = transferProgress)
            } else {
                BrowserProgressIndicator(visible = uiState.isLoading)
            }

            uiState.errorMessage?.let { message ->
                BrowserMessage(message = message, isError = true, onDismiss = null)
            }

            uiState.statusMessage?.let { message ->
                BrowserMessage(message = message, isError = false, onDismiss = null)
            }

            if (!uiState.isLoading && visibleFiles.isEmpty()) {
                BrowserEmptyState(
                    title = "This folder is empty",
                    description = "No files or folders in this SMB folder.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                FileCollection(
                    gridMode = gridMode,
                    files = visibleFiles,
                    thumbnailRepository = viewModel.thumbnailRepository,
                    selectedPaths = uiState.selectedPaths,
                    onFileClick = viewModel::onFileSelected,
                    onFileLongClick = viewModel::onFileLongPressed,
                    modifier = Modifier.fillMaxSize(),
                    scrollToTopKey = uiState.currentPath,
                    isRefreshing = uiState.isLoading,
                    onRefresh = viewModel::reload,
                )
            }
        }
    }
}

private fun SmbExplorerUiState.parentLabel(): String =
    if (pathStack.size <= 2) {
        "Share Root"
    } else {
        "Parent Folder"
    }
