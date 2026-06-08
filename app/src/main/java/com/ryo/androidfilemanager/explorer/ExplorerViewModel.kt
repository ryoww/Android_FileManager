package com.ryo.androidfilemanager.explorer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ryo.androidfilemanager.data.local.FileManagerAccess
import com.ryo.androidfilemanager.data.local.LocalFolderStore
import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.OpenedFile
import com.ryo.androidfilemanager.data.source.ExternalStorageFileSource
import com.ryo.androidfilemanager.data.source.FileSource
import com.ryo.androidfilemanager.data.source.LocalFileSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class ExplorerStorageMode {
    SAF,
    FILE_MANAGER,
}

data class ExplorerUiState(
    val hasFolderPermission: Boolean = false,
    val hasFullStorageAccess: Boolean = false,
    val storageMode: ExplorerStorageMode = ExplorerStorageMode.SAF,
    val rootName: String? = null,
    val currentPath: String? = null,
    val pathStack: List<String> = emptyList(),
    val files: List<FileItem> = emptyList(),
    val openedFile: OpenedFile? = null,
    val navigateUpLabel: String = "Parent Folder",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
) {
    val canNavigateUp: Boolean
        get() = pathStack.size > 1
}

class ExplorerViewModel(
    private val appContext: Context,
    private val folderStore: LocalFolderStore,
) : ViewModel() {
    var uiState by mutableStateOf(ExplorerUiState())
        private set

    private var source: FileSource? = null
    private var sourceMode: ExplorerStorageMode? = null
    private var savedTreeUriString: String? = null

    init {
        viewModelScope.launch {
            folderStore.rootTreeUri.collectLatest { uriString ->
                savedTreeUriString = uriString
                refreshStorageAccess()
            }
        }
    }

    fun onFolderSelected(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                persistFolderPermission(uri)
                folderStore.saveRootTreeUri(uri)
            }.onFailure { throwable ->
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = throwable.message
                        ?: "Local folder permission could not be saved. Choose the folder again.",
                )
            }
        }
    }

    fun onFileSelected(file: FileItem) {
        if (file.isDirectory) {
            openDirectory(file)
        } else {
            openFile(file)
        }
    }

    fun consumeOpenedFile() {
        uiState = uiState.copy(openedFile = null)
    }

    fun refreshStorageAccess() {
        val hasFullAccess = FileManagerAccess.hasAllFilesAccess()

        if (hasFullAccess) {
            if (sourceMode != ExplorerStorageMode.FILE_MANAGER) {
                loadDeviceStorage()
            } else {
                uiState = uiState.copy(hasFullStorageAccess = true)
            }
            return
        }

        if (sourceMode == ExplorerStorageMode.FILE_MANAGER) {
            source = null
            sourceMode = null
        }

        val uriString = savedTreeUriString
        if (uriString == null) {
            uiState = ExplorerUiState(
                hasFullStorageAccess = false,
                statusMessage = "Enable full storage access to browse Download directly, or choose a SAF-compatible folder.",
            )
        } else if (sourceMode != ExplorerStorageMode.SAF) {
            loadSafRoot(uriString)
        } else {
            uiState = uiState.copy(hasFullStorageAccess = false)
        }
    }

    fun navigateUp() {
        val nextStack = uiState.pathStack.dropLast(1)
        val nextPath = nextStack.lastOrNull() ?: return
        loadPath(path = nextPath, pathStack = nextStack)
    }

    fun reload() {
        val currentPath = uiState.currentPath ?: return
        loadPath(path = currentPath, pathStack = uiState.pathStack)
    }

    fun chooseAnotherFolder() {
        viewModelScope.launch {
            folderStore.clearRootTreeUri()
        }
    }

    private fun loadDeviceStorage() {
        viewModelScope.launch {
            runCatching {
                ExternalStorageFileSource()
            }.onSuccess { fileSource ->
                source = fileSource
                sourceMode = ExplorerStorageMode.FILE_MANAGER
                val startPath = fileSource.defaultStartPath
                val rootStack = if (startPath == fileSource.rootPath) {
                    listOf(fileSource.rootPath)
                } else {
                    listOf(fileSource.rootPath, startPath)
                }
                uiState = ExplorerUiState(
                    hasFolderPermission = true,
                    hasFullStorageAccess = true,
                    storageMode = ExplorerStorageMode.FILE_MANAGER,
                    rootName = fileSource.displayName(startPath),
                    currentPath = startPath,
                    pathStack = rootStack,
                    navigateUpLabel = fileSource.navigateUpLabel(rootStack),
                    isLoading = true,
                    statusMessage = "File manager access is enabled. Browsing Download with direct storage access.",
                )
                loadPath(path = startPath, pathStack = rootStack)
            }.onFailure { throwable ->
                source = null
                sourceMode = null
                uiState = ExplorerUiState(
                    hasFullStorageAccess = true,
                    errorMessage = throwable.message
                        ?: "Device storage could not be opened. Check full storage access.",
                )
            }
        }
    }

    private fun loadSafRoot(uriString: String) {
        viewModelScope.launch {
            runCatching {
                LocalFileSource(appContext, Uri.parse(uriString))
            }.onSuccess { localSource ->
                source = localSource
                sourceMode = ExplorerStorageMode.SAF
                val rootStack = listOf(localSource.rootPath)
                uiState = ExplorerUiState(
                    hasFolderPermission = true,
                    hasFullStorageAccess = false,
                    storageMode = ExplorerStorageMode.SAF,
                    rootName = localSource.rootName,
                    currentPath = localSource.rootPath,
                    pathStack = rootStack,
                    navigateUpLabel = "Parent Folder",
                    isLoading = true,
                )
                loadPath(path = localSource.rootPath, pathStack = rootStack)
            }.onFailure { throwable ->
                source = null
                sourceMode = null
                uiState = ExplorerUiState(
                    errorMessage = throwable.message
                        ?: "Local folder access failed. Choose the folder again.",
                )
            }
        }
    }

    private fun openDirectory(file: FileItem) {
        val nextStack = uiState.pathStack + file.path
        loadPath(path = file.path, pathStack = nextStack)
    }

    private fun openFile(file: FileItem) {
        val fileSource = source ?: return

        viewModelScope.launch {
            uiState = uiState.copy(
                isLoading = true,
                errorMessage = null,
                statusMessage = null,
            )

            runCatching {
                fileSource.open(file)
            }.onSuccess { openedFile ->
                uiState = uiState.copy(
                    openedFile = openedFile,
                    isLoading = false,
                )
            }.onFailure { throwable ->
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = throwable.message
                        ?: "File could not be opened. Check local folder permission and try again.",
                )
            }
        }
    }

    private fun loadPath(path: String, pathStack: List<String>) {
        val fileSource = source ?: return

        viewModelScope.launch {
            uiState = uiState.copy(
                isLoading = true,
                errorMessage = null,
                statusMessage = null,
            )

            runCatching {
                fileSource.list(path)
            }.onSuccess { files ->
                uiState = uiState.copy(
                    hasFolderPermission = true,
                    storageMode = sourceMode ?: uiState.storageMode,
                    rootName = displayNameFor(path),
                    currentPath = path,
                    pathStack = pathStack,
                    navigateUpLabel = navigateUpLabelFor(pathStack),
                    files = files,
                    isLoading = false,
                )
            }.onFailure { throwable ->
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = throwable.message
                        ?: "Local folder listing failed. Check folder permission and try again.",
                )
            }
        }
    }

    private fun displayNameFor(path: String): String? = when (val fileSource = source) {
        is ExternalStorageFileSource -> fileSource.displayName(path)
        is LocalFileSource -> fileSource.rootName
        else -> uiState.rootName
    }

    private fun navigateUpLabelFor(pathStack: List<String>): String = when (val fileSource = source) {
        is ExternalStorageFileSource -> fileSource.navigateUpLabel(pathStack)
        else -> "Parent Folder"
    }

    private fun persistFolderPermission(uri: Uri) {
        val resolver = appContext.contentResolver
        val readWriteFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        runCatching {
            resolver.takePersistableUriPermission(uri, readWriteFlags)
        }.recoverCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.getOrThrow()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                ExplorerViewModel(
                    appContext = appContext,
                    folderStore = LocalFolderStore(appContext),
                )
            }
        }
    }
}
