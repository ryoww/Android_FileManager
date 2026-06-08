package com.ryo.androidfilemanager.smb

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.OpenedFile
import com.ryo.androidfilemanager.data.smb.DefaultSmbClient
import com.ryo.androidfilemanager.data.smb.SmbConnectionInfo
import com.ryo.androidfilemanager.data.smb.SmbConnectionStore
import com.ryo.androidfilemanager.data.source.SmbFileSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SmbExplorerUiState(
    val host: String = "",
    val shareName: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val port: String = "445",
    val files: List<FileItem> = emptyList(),
    val currentPath: String = "",
    val pathStack: List<String> = emptyList(),
    val connected: Boolean = false,
    val connectionFormExpanded: Boolean = true,
    val hasSavedConnection: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val openedFile: OpenedFile? = null,
) {
    val canNavigateUp: Boolean
        get() = pathStack.size > 1
}

class SmbExplorerViewModel(
    private val appContext: Context,
    private val smbClient: DefaultSmbClient = DefaultSmbClient(),
    private val connectionStore: SmbConnectionStore = SmbConnectionStore(appContext),
) : ViewModel() {
    var uiState by mutableStateOf(SmbExplorerUiState())
        private set

    private var source: SmbFileSource? = null
    private var activeConnectionInfo: SmbConnectionInfo? = null

    fun currentSource(): SmbFileSource? = source

    init {
        viewModelScope.launch {
            connectionStore.savedConnection.first()?.let { info ->
                applyConnectionInfo(info)
                uiState = uiState.copy(
                    hasSavedConnection = true,
                    statusMessage = "Saved SMB connection loaded.",
                )
            }
        }
    }

    fun updateHost(value: String) {
        uiState = uiState.copy(host = value)
    }

    fun updateShareName(value: String) {
        uiState = uiState.copy(shareName = value)
    }

    fun updateUsername(value: String) {
        uiState = uiState.copy(username = value)
    }

    fun updatePassword(value: String) {
        uiState = uiState.copy(password = value)
    }

    fun updateDomain(value: String) {
        uiState = uiState.copy(domain = value)
    }

    fun updatePort(value: String) {
        uiState = uiState.copy(port = value.filter { it.isDigit() }.ifBlank { "445" })
    }

    fun testConnection() {
        viewModelScope.launch {
            val info = uiState.toConnectionInfoOrNull() ?: return@launch showInputError()
            uiState = uiState.copy(isLoading = true, errorMessage = null, statusMessage = null)

            smbClient.testConnection(info)
                .onSuccess {
                    connectionStore.saveConnection(info)
                    uiState = uiState.copy(
                        isLoading = false,
                        hasSavedConnection = true,
                        statusMessage = "SMB connection succeeded.",
                    )
                }
                .onFailure { throwable ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = throwable.message
                            ?: "SMB connection failed. Check host, share name, username, and password.",
                    )
                }
        }
    }

    fun connectAndListRoot() {
        viewModelScope.launch {
            val info = uiState.toConnectionInfoOrNull() ?: return@launch showInputError()
            activeConnectionInfo = info
            source = SmbFileSource(appContext, info)
            loadPath(path = "", pathStack = listOf(""))
        }
    }

    fun editConnection() {
        uiState = uiState.copy(connectionFormExpanded = true)
    }

    fun hideConnectionForm() {
        uiState = uiState.copy(connectionFormExpanded = false)
    }

    fun disconnect() {
        source = null
        activeConnectionInfo = null
        uiState = uiState.copy(
            files = emptyList(),
            currentPath = "",
            pathStack = emptyList(),
            connected = false,
            connectionFormExpanded = true,
            statusMessage = "Disconnected from SMB share.",
            errorMessage = null,
        )
    }

    fun clearSavedConnection() {
        viewModelScope.launch {
            connectionStore.clearConnection()
            source = null
            activeConnectionInfo = null
            uiState = SmbExplorerUiState(
                statusMessage = "Saved SMB connection was cleared.",
            )
        }
    }

    fun onFileSelected(file: FileItem) {
        if (file.isDirectory) {
            val nextStack = uiState.pathStack + file.path
            loadPath(path = file.path, pathStack = nextStack)
        } else {
            openFile(file)
        }
    }

    fun navigateUp() {
        val nextStack = uiState.pathStack.dropLast(1)
        val nextPath = nextStack.lastOrNull() ?: return
        loadPath(path = nextPath, pathStack = nextStack)
    }

    fun reload() {
        loadPath(path = uiState.currentPath, pathStack = uiState.pathStack.ifEmpty { listOf("") })
    }

    fun consumeOpenedFile() {
        uiState = uiState.copy(openedFile = null)
    }

    private fun loadPath(path: String, pathStack: List<String>) {
        val smbSource = source ?: return

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null, statusMessage = null)
            runCatching {
                smbSource.list(path)
            }.onSuccess { files ->
                activeConnectionInfo?.let { info ->
                    connectionStore.saveConnection(info)
                }
                uiState = uiState.copy(
                    files = files,
                    currentPath = path,
                    pathStack = pathStack,
                    connected = true,
                    connectionFormExpanded = false,
                    hasSavedConnection = true,
                    isLoading = false,
                )
            }.onFailure { throwable ->
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = throwable.message
                        ?: "SMB directory listing failed. Check connection and permissions.",
                )
            }
        }
    }

    private fun openFile(file: FileItem) {
        val smbSource = source ?: return

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null, statusMessage = null)
            runCatching {
                smbSource.open(file)
            }.onSuccess { openedFile ->
                uiState = uiState.copy(
                    openedFile = openedFile,
                    isLoading = false,
                )
            }.onFailure { throwable ->
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = throwable.message ?: "SMB file could not be opened.",
                )
            }
        }
    }

    private fun showInputError() {
        uiState = uiState.copy(
            errorMessage = "Host and share name are required. Port must be a valid number.",
        )
    }

    private fun applyConnectionInfo(info: SmbConnectionInfo) {
        uiState = uiState.copy(
            host = info.host,
            shareName = info.shareName,
            username = info.username.orEmpty(),
            password = info.password.orEmpty(),
            domain = info.domain.orEmpty(),
            port = info.port.toString(),
        )
    }

    private fun SmbExplorerUiState.toConnectionInfoOrNull(): SmbConnectionInfo? {
        val parsedPort = port.toIntOrNull() ?: return null
        if (host.isBlank() || shareName.isBlank()) {
            return null
        }

        return SmbConnectionInfo(
            host = host.trim(),
            shareName = shareName.trim(),
            username = username.takeIf { it.isNotBlank() },
            password = password.takeIf { it.isNotBlank() },
            domain = domain.takeIf { it.isNotBlank() },
            port = parsedPort,
        )
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SmbExplorerViewModel(context.applicationContext)
            }
        }
    }
}
