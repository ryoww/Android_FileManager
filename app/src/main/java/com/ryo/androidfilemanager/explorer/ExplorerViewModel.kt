package com.ryo.androidfilemanager.explorer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryo.androidfilemanager.data.local.FileManagerAccess
import com.ryo.androidfilemanager.data.local.LocalFolderStore
import com.ryo.androidfilemanager.core.application.BrowseOutcome
import com.ryo.androidfilemanager.core.application.DirectoryBrowser
import com.ryo.androidfilemanager.core.application.OpenEntryUseCase
import com.ryo.androidfilemanager.core.domain.DirectoryNavigation
import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.OpenedFile
import com.ryo.androidfilemanager.core.domain.ViewerType
import com.ryo.androidfilemanager.data.source.ExternalStorageFileSource
import com.ryo.androidfilemanager.core.application.port.FileSource
import com.ryo.androidfilemanager.data.source.LocalFileSource
import com.ryo.androidfilemanager.core.domain.detectViewerType
import com.ryo.androidfilemanager.data.thumbnail.ThumbnailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
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
    val navigation: DirectoryNavigation = DirectoryNavigation.Empty,
    val files: List<FileItem> = emptyList(),
    val openedFile: OpenedFile? = null,
    val navigateUpLabel: String = "Parent Folder",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
) {
    val currentPath: String?
        get() = navigation.currentPath

    val canNavigateUp: Boolean
        get() = navigation.canNavigateUp
}

class ExplorerViewModel(
    private val appContext: Context,
    private val folderStore: LocalFolderStore,
    val thumbnailRepository: ThumbnailRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private var source: FileSource? = null
    private var browser: DirectoryBrowser? = null
    private var openEntry: OpenEntryUseCase? = null
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
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message
                            ?: "Local folder permission could not be saved. Choose the folder again.",
                    )
                }
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
        _uiState.update { it.copy(openedFile = null) }
    }

    fun refreshStorageAccess() {
        val hasFullAccess = FileManagerAccess.hasAllFilesAccess()

        if (hasFullAccess) {
            if (sourceMode != ExplorerStorageMode.FILE_MANAGER) {
                loadDeviceStorage()
            } else {
                _uiState.update { it.copy(hasFullStorageAccess = true) }
            }
            return
        }

        if (sourceMode == ExplorerStorageMode.FILE_MANAGER) {
            source = null
            browser = null
            openEntry = null
            sourceMode = null
        }

        val uriString = savedTreeUriString
        if (uriString == null) {
            _uiState.value = ExplorerUiState(
                hasFullStorageAccess = false,
                statusMessage = "Enable full storage access to browse Download directly, or choose a SAF-compatible folder.",
            )
        } else if (sourceMode != ExplorerStorageMode.SAF) {
            loadSafRoot(uriString)
        } else {
            _uiState.update { it.copy(hasFullStorageAccess = false) }
        }
    }

    fun navigateUp() {
        val directoryBrowser = browser ?: return
        val state = _uiState.value
        if (!state.navigation.canNavigateUp) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, statusMessage = null)
            }
            runCatching {
                directoryBrowser.up(state.navigation)
            }.onSuccess { outcome ->
                if (outcome == null) return@launch
                applyBrowseOutcome(outcome)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message
                            ?: "Local folder listing failed. Check folder permission and try again.",
                    )
                }
            }
        }
    }

    fun reload() {
        val directoryBrowser = browser ?: return
        if (_uiState.value.currentPath == null) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, statusMessage = null)
            }
            runCatching {
                directoryBrowser.reload(_uiState.value.navigation)
            }.onSuccess { outcome ->
                applyBrowseOutcome(outcome)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message
                            ?: "Local folder listing failed. Check folder permission and try again.",
                    )
                }
            }
        }
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
                browser = DirectoryBrowser(fileSource)
                openEntry = OpenEntryUseCase(fileSource)
                sourceMode = ExplorerStorageMode.FILE_MANAGER
                val startPath = fileSource.defaultStartPath
                val rootStack = if (startPath == fileSource.rootPath) {
                    listOf(fileSource.rootPath)
                } else {
                    listOf(fileSource.rootPath, startPath)
                }
                val rootNavigation = DirectoryNavigation(rootStack)
                _uiState.value = ExplorerUiState(
                    hasFolderPermission = true,
                    hasFullStorageAccess = true,
                    storageMode = ExplorerStorageMode.FILE_MANAGER,
                    rootName = fileSource.displayName(startPath),
                    navigation = rootNavigation,
                    navigateUpLabel = fileSource.navigateUpLabel(rootStack),
                    isLoading = true,
                    statusMessage = "File manager access is enabled. Browsing Download with direct storage access.",
                )
                loadInitial(rootNavigation)
            }.onFailure { throwable ->
                source = null
                browser = null
                openEntry = null
                sourceMode = null
                _uiState.value = ExplorerUiState(
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
                browser = DirectoryBrowser(localSource)
                openEntry = OpenEntryUseCase(localSource)
                sourceMode = ExplorerStorageMode.SAF
                val rootNavigation = DirectoryNavigation.root(localSource.rootPath)
                _uiState.value = ExplorerUiState(
                    hasFolderPermission = true,
                    hasFullStorageAccess = false,
                    storageMode = ExplorerStorageMode.SAF,
                    rootName = localSource.rootName,
                    navigation = rootNavigation,
                    navigateUpLabel = "Parent Folder",
                    isLoading = true,
                )
                loadInitial(rootNavigation)
            }.onFailure { throwable ->
                source = null
                browser = null
                openEntry = null
                sourceMode = null
                _uiState.value = ExplorerUiState(
                    errorMessage = throwable.message
                        ?: "Local folder access failed. Choose the folder again.",
                )
            }
        }
    }

    // ルート読み込み直後は DirectoryBrowser.reload を使い、pathStack を変えずに一覧だけ取得する
    private fun loadInitial(navigation: DirectoryNavigation) {
        val directoryBrowser = browser ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, statusMessage = null)
            }
            runCatching {
                directoryBrowser.reload(navigation)
            }.onSuccess { outcome ->
                applyBrowseOutcome(outcome)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message
                            ?: "Local folder listing failed. Check folder permission and try again.",
                    )
                }
            }
        }
    }

    private fun openDirectory(file: FileItem) {
        val directoryBrowser = browser ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, statusMessage = null)
            }
            runCatching {
                directoryBrowser.enter(_uiState.value.navigation, file.path)
            }.onSuccess { outcome ->
                applyBrowseOutcome(outcome)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message
                            ?: "Local folder listing failed. Check folder permission and try again.",
                    )
                }
            }
        }
    }

    private fun openFile(file: FileItem) {
        val openEntryUseCase = openEntry ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, statusMessage = null)
            }

            runCatching {
                openEntryUseCase(file)
            }.onSuccess { openedFile ->
                _uiState.update { it.copy(openedFile = openedFile, isLoading = false) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message
                            ?: "File could not be opened. Check local folder permission and try again.",
                    )
                }
            }
        }
    }

    private fun applyBrowseOutcome(outcome: BrowseOutcome) {
        val path = requireNotNull(outcome.navigation.currentPath)
        _uiState.update {
            it.copy(
                hasFolderPermission = true,
                storageMode = sourceMode ?: it.storageMode,
                rootName = displayNameFor(path),
                navigation = outcome.navigation,
                navigateUpLabel = navigateUpLabelFor(outcome.navigation.pathStack),
                files = outcome.entries,
                isLoading = false,
            )
        }
        prefetchPdfThumbnails(outcome.entries)
    }

    private fun prefetchPdfThumbnails(files: List<FileItem>) {
        files
            .asSequence()
            .filterNot { it.isDirectory }
            .filter { file -> detectViewerType(file.name, file.mimeType) == ViewerType.Pdf }
            .sortedWith(
                compareByDescending<FileItem> { it.modifiedAt ?: Long.MIN_VALUE }
                    .thenBy { it.name.lowercase() },
            )
            .take(PDF_PREFETCH_LIMIT)
            .forEach { file -> thumbnailRepository.requestThumbnail(file) }
    }

    private fun displayNameFor(path: String): String? = when (val fileSource = source) {
        is ExternalStorageFileSource -> fileSource.displayName(path)
        is LocalFileSource -> fileSource.rootName
        else -> _uiState.value.rootName
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
        private const val PDF_PREFETCH_LIMIT = 24
    }
}
