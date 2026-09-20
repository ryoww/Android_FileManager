package com.ryo.androidfilemanager.smb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryo.androidfilemanager.core.application.BrowseOutcome
import com.ryo.androidfilemanager.core.application.ConnectToShareUseCase
import com.ryo.androidfilemanager.core.application.DirectoryBrowser
import com.ryo.androidfilemanager.core.application.DownloadDestinationUnavailableException
import com.ryo.androidfilemanager.core.application.DownloadEntriesUseCase
import com.ryo.androidfilemanager.core.application.NoEntriesSelectedException
import com.ryo.androidfilemanager.core.application.OpenEntryUseCase
import com.ryo.androidfilemanager.core.application.ProgressThrottle
import com.ryo.androidfilemanager.core.application.UploadEntriesUseCase
import com.ryo.androidfilemanager.core.application.port.SmbClient
import com.ryo.androidfilemanager.core.application.port.SmbConnectionRepository
import com.ryo.androidfilemanager.core.application.port.SmbShareAccess
import com.ryo.androidfilemanager.core.application.port.SmbShareConnector
import com.ryo.androidfilemanager.core.domain.DirectoryNavigation
import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.FileSelection
import com.ryo.androidfilemanager.core.domain.OpenedFile
import com.ryo.androidfilemanager.core.domain.SmbConnectionForm
import com.ryo.androidfilemanager.core.domain.SmbConnectionInfo
import com.ryo.androidfilemanager.core.domain.TransferProgress
import com.ryo.androidfilemanager.core.domain.ViewerType
import com.ryo.androidfilemanager.core.domain.detectViewerType
import com.ryo.androidfilemanager.data.thumbnail.ThumbnailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SmbExplorerUiState(
    val form: SmbConnectionForm = SmbConnectionForm(),
    val files: List<FileItem> = emptyList(),
    val selection: FileSelection = FileSelection(),
    val navigation: DirectoryNavigation = DirectoryNavigation.Empty,
    val connected: Boolean = false,
    val connectionFormExpanded: Boolean = true,
    val hasSavedConnection: Boolean = false,
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val isUploading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val openedFile: OpenedFile? = null,
    val transferProgress: TransferProgress? = null,
) {
    val currentPath: String
        get() = navigation.currentPath ?: ""

    val pathStack: List<String>
        get() = navigation.pathStack

    val canNavigateUp: Boolean
        get() = navigation.canNavigateUp

    val selectedPaths: Set<String>
        get() = selection.paths

    val selectedCount: Int
        get() = selection.count
}

class SmbExplorerViewModel(
    private val smbClient: SmbClient,
    private val connectionStore: SmbConnectionRepository,
    private val shareConnector: SmbShareConnector,
    thumbnailRepositoryFactory: (sourceProvider: () -> SmbShareAccess?) -> ThumbnailRepository,
    private val canWriteDownloads: () -> Boolean,
    private val progressThrottle: ProgressThrottle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SmbExplorerUiState())
    val uiState: StateFlow<SmbExplorerUiState> = _uiState.asStateFlow()

    private var source: SmbShareAccess? = null
    private var browser: DirectoryBrowser? = null
    private var openEntry: OpenEntryUseCase? = null
    private var downloadEntries: DownloadEntriesUseCase? = null
    private var uploadEntries: UploadEntriesUseCase? = null
    val thumbnailRepository: ThumbnailRepository = thumbnailRepositoryFactory { source }
    private var activeConnectionInfo: SmbConnectionInfo? = null

    // 転送は IO スレッドから呼ばれるが、StateFlow.update は CAS で原子的なのでそのまま反映する。
    // Why not viewModelScope.launch: Main に寄せると、転送完了後の「進捗を消す」更新より
    // 後に遅れて届いた進捗フレームが残留し得る。完了フレームは間引かず必ず通す
    private fun reportProgress(progress: TransferProgress) {
        if (!progressThrottle.shouldEmit(progress)) return
        _uiState.update { it.copy(transferProgress = progress) }
    }

    init {
        viewModelScope.launch {
            connectionStore.savedConnection.first()?.let { info ->
                applyConnectionInfo(info)
                _uiState.update {
                    it.copy(
                        hasSavedConnection = true,
                        statusMessage = "Saved SMB connection loaded.",
                    )
                }
            }
        }
    }

    fun updateHost(value: String) {
        _uiState.update { it.copy(form = it.form.copy(host = value)) }
    }

    fun updateShareName(value: String) {
        _uiState.update { it.copy(form = it.form.copy(shareName = value)) }
    }

    fun updateUsername(value: String) {
        _uiState.update { it.copy(form = it.form.copy(username = value)) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(form = it.form.copy(password = value)) }
    }

    fun updateDomain(value: String) {
        _uiState.update { it.copy(form = it.form.copy(domain = value)) }
    }

    fun updatePort(value: String) {
        _uiState.update { it.copy(form = it.form.withPortInput(value)) }
    }

    fun testConnection() {
        viewModelScope.launch {
            val form = _uiState.value.form
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }

            ConnectToShareUseCase(smbClient, connectionStore).test(form)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasSavedConnection = true,
                            statusMessage = "SMB connection succeeded.",
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message
                                ?: "SMB connection failed. Check host, share name, username, and password.",
                        )
                    }
                }
        }
    }

    fun connectAndListRoot() {
        viewModelScope.launch {
            val form = _uiState.value.form
            val info = form.toConnectionInfo().getOrElse { return@launch showInputError() }
            activeConnectionInfo = info
            // 接続情報の保存は接続時の 1 回だけ。一覧取得のたびに平文パスワードを書き直さない。
            // DataStore への書き込み失敗で一覧まで落とさないよう、失敗は保存済みフラグに留める
            val saved = runCatching { connectionStore.save(info) }.isSuccess
            _uiState.update { it.copy(hasSavedConnection = it.hasSavedConnection || saved) }
            val fileSource = shareConnector.connect(info)
            source = fileSource
            browser = DirectoryBrowser(fileSource)
            openEntry = OpenEntryUseCase(fileSource)
            downloadEntries = DownloadEntriesUseCase(fileSource, canWriteDownloads)
            uploadEntries = UploadEntriesUseCase(fileSource)
            loadRoot()
        }
    }

    fun editConnection() {
        _uiState.update { it.copy(connectionFormExpanded = true) }
    }

    fun hideConnectionForm() {
        _uiState.update { it.copy(connectionFormExpanded = false) }
    }

    fun disconnect() {
        source = null
        browser = null
        openEntry = null
        downloadEntries = null
        uploadEntries = null
        activeConnectionInfo = null
        shareConnector.disconnectAll()
        _uiState.update {
            it.copy(
                files = emptyList(),
                selection = FileSelection(),
                navigation = DirectoryNavigation.Empty,
                connected = false,
                connectionFormExpanded = true,
                statusMessage = "Disconnected from SMB share.",
                errorMessage = null,
            )
        }
    }

    fun clearSavedConnection() {
        viewModelScope.launch {
            connectionStore.clear()
            source = null
            browser = null
            openEntry = null
            downloadEntries = null
            uploadEntries = null
            activeConnectionInfo = null
            shareConnector.disconnectAll()
            _uiState.value = SmbExplorerUiState(
                statusMessage = "Saved SMB connection was cleared.",
            )
        }
    }

    fun onFileSelected(file: FileItem) {
        if (_uiState.value.selection.isActive) {
            toggleSelection(file)
            return
        }

        if (file.isDirectory) {
            openDirectory(file)
        } else {
            openFile(file)
        }
    }

    fun onFileLongPressed(file: FileItem) {
        toggleSelection(file)
    }

    fun clearSelection() {
        _uiState.update { it.copy(selection = it.selection.clear()) }
    }

    fun navigateUp() {
        val directoryBrowser = browser ?: return
        val state = _uiState.value
        if (!state.navigation.canNavigateUp) return

        browse { directoryBrowser.up(state.navigation) }
    }

    fun reload() {
        // 明示的なリロードでは、TTL 中の失敗サムネイルも再試行対象に戻す
        thumbnailRepository.resetFailedThumbnails()
        val directoryBrowser = browser ?: return
        val navigation = _uiState.value.navigation.let {
            if (it.pathStack.isEmpty()) DirectoryNavigation.root("") else it
        }

        browse { directoryBrowser.reload(navigation) }
    }

    fun consumeOpenedFile() {
        _uiState.update { it.copy(openedFile = null) }
    }

    fun downloadSelectedFiles() {
        val downloadEntriesUseCase = downloadEntries ?: return
        val state = _uiState.value
        // 検証失敗時に「早期 return と同じ見え方」へ戻すため、書き換え前の statusMessage を控えておく
        val previousStatusMessage = state.statusMessage

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isDownloading = true,
                    errorMessage = null,
                    statusMessage = "Downloading ${state.selection.count} selected item(s)...",
                )
            }

            runCatching {
                downloadEntriesUseCase(state.selection, state.files, ::reportProgress)
            }.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        selection = FileSelection(),
                        isLoading = false,
                        isDownloading = false,
                        statusMessage = "Downloaded ${summary.fileCount} file(s) to ${summary.destinationPath}.",
                        transferProgress = null,
                    )
                }
            }.onFailure { throwable ->
                // 検証失敗（未選択・書き込み不可）は転送前に判明するため、isLoading/isDownloading と
                // statusMessage を呼び出し前の値へ戻し、早期 return していた頃と同じ見え方にする
                when (throwable) {
                    is NoEntriesSelectedException -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            isDownloading = false,
                            errorMessage = "Select SMB files or folders to download.",
                            statusMessage = previousStatusMessage,
                        )
                    }
                    is DownloadDestinationUnavailableException -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            isDownloading = false,
                            errorMessage = "Enable full storage access before downloading SMB files to Download.",
                            statusMessage = previousStatusMessage,
                        )
                    }
                    else -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            isDownloading = false,
                            errorMessage = throwable.message ?: "SMB download failed.",
                            transferProgress = null,
                        )
                    }
                }
            }
        }
    }

    fun uploadFiles(sources: List<String>) {
        val uploadEntriesUseCase = uploadEntries ?: return
        val directoryBrowser = browser ?: return
        if (sources.isEmpty()) {
            return
        }

        val state = _uiState.value
        val destinationNavigation = if (state.navigation.pathStack.isEmpty()) {
            DirectoryNavigation.root("")
        } else {
            state.navigation
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isUploading = true,
                    errorMessage = null,
                    statusMessage = "Uploading ${sources.size} selected file(s)...",
                )
            }

            runCatching {
                uploadEntriesUseCase(sources, destinationNavigation, ::reportProgress)
            }.onSuccess { summary ->
                _uiState.update { it.copy(isUploading = false, transferProgress = null) }
                runCatching {
                    directoryBrowser.reload(destinationNavigation)
                }.onSuccess { outcome ->
                    applyBrowseOutcome(
                        outcome,
                        successMessage = "Uploaded ${summary.fileCount} file(s) to ${summary.destinationPath}.",
                    )
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message
                                ?: "SMB directory listing failed. Check connection and permissions.",
                        )
                    }
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isUploading = false,
                        errorMessage = throwable.message ?: "SMB upload failed.",
                        transferProgress = null,
                    )
                }
            }
        }
    }

    private fun loadRoot() {
        val directoryBrowser = browser ?: return
        val navigation = DirectoryNavigation.root("")

        browse { directoryBrowser.reload(navigation) }
    }

    private fun openDirectory(file: FileItem) {
        val directoryBrowser = browser ?: return

        browse { directoryBrowser.enter(_uiState.value.navigation, file.path) }
    }

    // 4 つの遷移（ルート読込 / 階層移動 / 上へ / 再読込）は「読み込み中にして取得し、
    // 成功なら状態へ反映、失敗なら同じ文言でエラー表示」という流れが同じなので 1 箇所にまとめる
    private fun browse(load: suspend () -> BrowseOutcome?) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selection = FileSelection(),
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = null,
                )
            }
            runCatching { load() }
                .onSuccess { outcome ->
                    if (outcome != null) {
                        applyBrowseOutcome(outcome)
                    } else {
                        // 遷移なし（ルートで「上へ」など）。読み込み中表示だけ戻す
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message
                                ?: "SMB directory listing failed. Check connection and permissions.",
                        )
                    }
                }
        }
    }

    private fun applyBrowseOutcome(outcome: BrowseOutcome, successMessage: String? = null) {
        _uiState.update {
            it.copy(
                files = outcome.entries,
                selection = FileSelection(),
                navigation = outcome.navigation,
                connected = true,
                connectionFormExpanded = false,
                isLoading = false,
                statusMessage = successMessage,
            )
        }
        prefetchSmbPdfThumbnails(outcome.entries)
    }

    private fun prefetchSmbPdfThumbnails(files: List<FileItem>) {
        files
            .asSequence()
            .filterNot { it.isDirectory }
            .filter { file -> detectViewerType(file.name, file.mimeType) == ViewerType.Pdf }
            .sortedWith(
                compareByDescending<FileItem> { it.modifiedAt ?: Long.MIN_VALUE }
                    .thenBy { it.name.lowercase() },
            )
            .take(SMB_PDF_PREFETCH_LIMIT)
            .forEach { file -> thumbnailRepository.requestThumbnail(file) }
    }

    private fun openFile(file: FileItem) {
        val openEntryUseCase = openEntry ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
            runCatching {
                openEntryUseCase(file, ::reportProgress)
            }.onSuccess { openedFile ->
                _uiState.update {
                    it.copy(
                        openedFile = openedFile,
                        isLoading = false,
                        transferProgress = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "SMB file could not be opened.",
                        transferProgress = null,
                    )
                }
            }
        }
    }

    private fun showInputError() {
        _uiState.update {
            it.copy(errorMessage = "Host and share name are required. Port must be a valid number.")
        }
    }

    private fun toggleSelection(file: FileItem) {
        _uiState.update {
            it.copy(selection = it.selection.toggle(file.path), errorMessage = null)
        }
    }

    private fun applyConnectionInfo(info: SmbConnectionInfo) {
        _uiState.update { it.copy(form = SmbConnectionForm.from(info)) }
    }

    override fun onCleared() {
        thumbnailRepository.close()
        shareConnector.disconnectAll()
        super.onCleared()
    }

    companion object {
        private const val SMB_PDF_PREFETCH_LIMIT = 12
    }
}
