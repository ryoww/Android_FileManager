package com.ryo.androidfilemanager.smb

import androidx.lifecycle.ViewModelStore
import com.ryo.androidfilemanager.core.application.ProgressThrottle
import com.ryo.androidfilemanager.core.application.port.SmbClient
import com.ryo.androidfilemanager.core.application.port.SmbConnectionRepository
import com.ryo.androidfilemanager.core.application.port.SmbShareAccess
import com.ryo.androidfilemanager.core.application.port.SmbShareConnector
import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.OpenedFile
import com.ryo.androidfilemanager.core.domain.SmbConnectionInfo
import com.ryo.androidfilemanager.core.domain.SourceType
import com.ryo.androidfilemanager.core.domain.TransferKind
import com.ryo.androidfilemanager.core.domain.TransferProgress
import com.ryo.androidfilemanager.core.domain.TransferSummary
import com.ryo.androidfilemanager.core.domain.detectViewerType
import com.ryo.androidfilemanager.data.thumbnail.ThumbnailRepository
import com.ryo.androidfilemanager.data.thumbnail.ThumbnailResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeSmbClient(private val result: Result<Unit>) : SmbClient {
    override suspend fun testConnection(info: SmbConnectionInfo): Result<Unit> = result
}

private class InMemorySmbConnectionRepository(
    initial: SmbConnectionInfo? = null,
) : SmbConnectionRepository {
    private val state = MutableStateFlow(initial)
    override val savedConnection = state
    var saveCount = 0
        private set

    override suspend fun save(info: SmbConnectionInfo) {
        saveCount++
        state.value = info
    }

    override suspend fun clear() {
        state.value = null
    }
}

private class FakeShareAccess : SmbShareAccess {
    val directories = mutableMapOf<String, List<FileItem>>()
    var listFailure: Throwable? = null
    val listedPaths = mutableListOf<String>()
    val downloadedFiles = mutableListOf<FileItem>()
    var progressFrames: List<TransferProgress> = emptyList()
    // 進捗を通知した直後に呼ぶフック。転送中の uiState を観測するために使う
    var afterProgress: (() -> Unit)? = null
    var uploadedSources: List<String>? = null
    var uploadedDestination: String? = null

    override suspend fun list(path: String): List<FileItem> {
        listedPaths.add(path)
        listFailure?.let { throw it }
        return directories[path] ?: emptyList()
    }

    override suspend fun open(
        file: FileItem,
        onProgress: ((TransferProgress) -> Unit)?,
    ): OpenedFile = OpenedFile.Local(
        uri = "file:///cache/${file.name}",
        viewerType = detectViewerType(file.name, file.mimeType),
    )

    override suspend fun download(
        files: List<FileItem>,
        onProgress: ((TransferProgress) -> Unit)?,
    ): TransferSummary {
        downloadedFiles.addAll(files)
        progressFrames.forEach {
            onProgress?.invoke(it)
            afterProgress?.invoke()
        }
        return TransferSummary(files.size, "/Download/x")
    }

    override suspend fun upload(
        sources: List<String>,
        remoteDirectoryPath: String,
        onProgress: ((TransferProgress) -> Unit)?,
    ): TransferSummary {
        uploadedSources = sources
        uploadedDestination = remoteDirectoryPath
        return TransferSummary(sources.size, remoteDirectoryPath.ifBlank { "Share Root" })
    }
}

private class FakeShareConnector(val access: FakeShareAccess = FakeShareAccess()) : SmbShareConnector {
    val connectedInfos = mutableListOf<SmbConnectionInfo>()
    var disconnectCount = 0
        private set

    override fun connect(info: SmbConnectionInfo): SmbShareAccess {
        connectedInfos.add(info)
        return access
    }

    override fun disconnectAll() {
        disconnectCount++
    }
}

private class NoopThumbnailRepository : ThumbnailRepository {
    var closeCount = 0
        private set
    private val version = MutableStateFlow(0L)

    override suspend fun getThumbnail(file: FileItem): ThumbnailResult =
        ThumbnailResult.Unavailable(reason = "noop")

    override suspend fun generateThumbnail(file: FileItem): ThumbnailResult =
        ThumbnailResult.Unavailable(reason = "noop")

    override fun requestThumbnail(file: FileItem) {}

    override fun observeThumbnailVersion(): StateFlow<Long> = version

    override suspend fun clearThumbnailCache() {}

    override fun close() {
        closeCount++
    }
}

private fun file(name: String, path: String = "/$name", isDirectory: Boolean = false) = FileItem(
    name = name,
    path = path,
    uri = null,
    isDirectory = isDirectory,
    size = if (isDirectory) null else 10L,
    modifiedAt = null,
    mimeType = null,
    sourceType = SourceType.SMB,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SmbExplorerViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(
        connector: FakeShareConnector,
        repository: InMemorySmbConnectionRepository = InMemorySmbConnectionRepository(),
        canWriteDownloads: () -> Boolean = { true },
        smbClient: SmbClient = FakeSmbClient(Result.success(Unit)),
        thumbnailRepository: NoopThumbnailRepository = NoopThumbnailRepository(),
    ) = SmbExplorerViewModel(
        smbClient = smbClient,
        connectionStore = repository,
        shareConnector = connector,
        thumbnailRepositoryFactory = { thumbnailRepository },
        canWriteDownloads = canWriteDownloads,
        progressThrottle = ProgressThrottle(intervalMs = 0L, now = { 0L }),
    )

    private fun fillValidForm(vm: SmbExplorerViewModel) {
        vm.updateHost("nas.local")
        vm.updateShareName("share")
        vm.updateUsername("user")
        vm.updatePassword("secret")
    }

    @Test
    fun `保存済み接続があれば起動時にフォームへ読み込まれ接続済みフラグが立つ`() = runTest {
        val saved = SmbConnectionInfo(host = "nas.local", shareName = "share", username = "user", password = "secret", domain = null)
        val repository = InMemorySmbConnectionRepository(initial = saved)
        val viewModel = newViewModel(FakeShareConnector(), repository = repository)

        val state = viewModel.uiState.value

        assertEquals("nas.local", state.form.host)
        assertTrue(state.hasSavedConnection)
    }

    @Test
    fun `有効なフォームで接続するとルート一覧が表示され接続情報は1回だけ保存される`() = runTest {
        val connector = FakeShareConnector()
        connector.access.directories[""] = listOf(file("a.txt"))
        connector.access.directories["/child"] = listOf(file("b.txt"))
        val repository = InMemorySmbConnectionRepository()
        val viewModel = newViewModel(connector, repository = repository)
        fillValidForm(viewModel)

        viewModel.connectAndListRoot()

        assertTrue(viewModel.uiState.value.connected)
        assertEquals(listOf("a.txt"), viewModel.uiState.value.files.map { it.name })
        assertEquals(1, repository.saveCount)

        viewModel.onFileSelected(file("child", path = "/child", isDirectory = true))

        assertEquals(1, repository.saveCount)
    }

    @Test
    fun `ホストが空のまま接続するとエラーになり接続は呼ばれない`() = runTest {
        val connector = FakeShareConnector()
        val viewModel = newViewModel(connector)
        viewModel.updateShareName("share")

        viewModel.connectAndListRoot()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(connector.connectedInfos.isEmpty())
    }

    @Test
    fun `フォルダに入ると一覧とパススタックが更新されnavigateUpで元に戻る`() = runTest {
        val connector = FakeShareConnector()
        connector.access.directories[""] = listOf(file("child", path = "/child", isDirectory = true))
        connector.access.directories["/child"] = listOf(file("inner.txt", path = "/child/inner.txt"))
        val viewModel = newViewModel(connector)
        fillValidForm(viewModel)
        viewModel.connectAndListRoot()

        viewModel.onFileSelected(file("child", path = "/child", isDirectory = true))

        assertEquals(listOf("inner.txt"), viewModel.uiState.value.files.map { it.name })
        assertEquals(listOf("", "/child"), viewModel.uiState.value.pathStack)

        viewModel.navigateUp()

        assertEquals(listOf("child"), viewModel.uiState.value.files.map { it.name })
        assertEquals(listOf(""), viewModel.uiState.value.pathStack)
    }

    @Test
    fun `一覧取得が失敗すると例外メッセージがそのままエラーになりローディングが解除される`() = runTest {
        val connector = FakeShareConnector()
        connector.access.listFailure = IllegalStateException("boom")
        val viewModel = newViewModel(connector)
        fillValidForm(viewModel)

        viewModel.connectAndListRoot()

        assertEquals("boom", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `ファイルを選ぶとopenedFileが設定されconsumeOpenedFileで消える`() = runTest {
        val connector = FakeShareConnector()
        connector.access.directories[""] = listOf(file("doc.txt"))
        val viewModel = newViewModel(connector)
        fillValidForm(viewModel)
        viewModel.connectAndListRoot()

        viewModel.onFileSelected(file("doc.txt"))

        assertNotNull(viewModel.uiState.value.openedFile)

        viewModel.consumeOpenedFile()

        assertNull(viewModel.uiState.value.openedFile)
    }

    @Test
    fun `未選択でダウンロードすると案内のエラーが出るだけで転送されない`() = runTest {
        val connector = FakeShareConnector()
        connector.access.directories[""] = listOf(file("doc.txt"))
        val viewModel = newViewModel(connector)
        fillValidForm(viewModel)
        viewModel.connectAndListRoot()

        viewModel.downloadSelectedFiles()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(connector.access.downloadedFiles.isEmpty())
    }

    @Test
    fun `ダウンロード先へ書き込めないと転送せずエラーになる`() = runTest {
        val connector = FakeShareConnector()
        connector.access.directories[""] = listOf(file("doc.txt"))
        val viewModel = newViewModel(connector, canWriteDownloads = { false })
        fillValidForm(viewModel)
        viewModel.connectAndListRoot()
        viewModel.onFileLongPressed(file("doc.txt"))

        viewModel.downloadSelectedFiles()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(connector.access.downloadedFiles.isEmpty())
    }

    @Test
    fun `長押しで選択した項目をダウンロードすると進捗が反映され完了後は選択と進捗がクリアされる`() = runTest {
        val connector = FakeShareConnector()
        connector.access.directories[""] = listOf(file("doc.txt"))
        connector.access.progressFrames = listOf(
            TransferProgress(
                kind = TransferKind.DOWNLOAD,
                fileName = "doc.txt",
                bytesTransferred = 5L,
                totalBytes = 10L,
            ),
        )
        val viewModel = newViewModel(connector)
        fillValidForm(viewModel)
        viewModel.connectAndListRoot()
        viewModel.onFileLongPressed(file("doc.txt"))

        // StateFlow は最新値しか保持しないので、転送中の状態は進捗通知の直後に読む
        var observedDuringTransfer: TransferProgress? = null
        connector.access.afterProgress = {
            observedDuringTransfer = viewModel.uiState.value.transferProgress
        }

        viewModel.downloadSelectedFiles()

        assertEquals(5L, observedDuringTransfer?.bytesTransferred)

        assertEquals(1, connector.access.downloadedFiles.size)
        assertTrue(viewModel.uiState.value.selection.paths.isEmpty())
        assertNull(viewModel.uiState.value.transferProgress)
        assertTrue(viewModel.uiState.value.statusMessage?.contains("1") == true)
    }

    @Test
    fun `uploadFilesの後は同じフォルダを再読込しアップロード完了メッセージを出す`() = runTest {
        val connector = FakeShareConnector()
        connector.access.directories[""] = emptyList()
        val viewModel = newViewModel(connector)
        fillValidForm(viewModel)
        viewModel.connectAndListRoot()

        viewModel.uploadFiles(listOf("content://a"))

        assertEquals(listOf("content://a"), connector.access.uploadedSources)
        assertEquals("", connector.access.uploadedDestination)
        assertFalse(viewModel.uiState.value.isUploading)
        assertTrue(viewModel.uiState.value.statusMessage?.contains("1") == true)
    }

    @Test
    fun `disconnectすると一覧が空になり接続が解除される`() = runTest {
        val connector = FakeShareConnector()
        connector.access.directories[""] = listOf(file("doc.txt"))
        val viewModel = newViewModel(connector)
        fillValidForm(viewModel)
        viewModel.connectAndListRoot()

        viewModel.disconnect()

        assertTrue(viewModel.uiState.value.files.isEmpty())
        assertFalse(viewModel.uiState.value.connected)
        assertEquals(1, connector.disconnectCount)
    }

    @Test
    fun `ViewModelが破棄されるとサムネイルリポジトリとコネクタが解放される`() = runTest {
        val connector = FakeShareConnector()
        val thumbnailRepository = NoopThumbnailRepository()
        val viewModel = newViewModel(connector, thumbnailRepository = thumbnailRepository)
        val store = ViewModelStore()
        store.put("smb", viewModel)

        store.clear()

        assertEquals(1, thumbnailRepository.closeCount)
        assertEquals(1, connector.disconnectCount)
    }
}
