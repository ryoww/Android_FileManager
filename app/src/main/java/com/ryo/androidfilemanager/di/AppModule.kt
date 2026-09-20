package com.ryo.androidfilemanager.di

import android.os.SystemClock
import com.ryo.androidfilemanager.core.application.ProgressThrottle
import com.ryo.androidfilemanager.core.application.port.SmbClient
import com.ryo.androidfilemanager.core.application.port.SmbConnectionRepository
import com.ryo.androidfilemanager.core.application.port.SmbShareConnector
import com.ryo.androidfilemanager.data.cache.FileCacheRepository
import com.ryo.androidfilemanager.data.local.FileManagerAccess
import com.ryo.androidfilemanager.data.local.LocalFolderStore
import com.ryo.androidfilemanager.data.smb.DefaultSmbClient
import com.ryo.androidfilemanager.data.smb.DefaultSmbShareConnector
import com.ryo.androidfilemanager.data.smb.SmbConnectionStore
import com.ryo.androidfilemanager.data.source.SmbFileSource
import com.ryo.androidfilemanager.data.thumbnail.FileThumbnailRepository
import com.ryo.androidfilemanager.data.thumbnail.SmbThumbnailRepository
import com.ryo.androidfilemanager.explorer.ExplorerViewModel
import com.ryo.androidfilemanager.smb.SmbExplorerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

private const val PROGRESS_EMIT_INTERVAL_MS = 100L

val appModule = module {
    single { LocalFolderStore(androidContext()) }
    single<SmbConnectionRepository> { SmbConnectionStore(androidContext()) }
    single<SmbClient> { DefaultSmbClient() }
    single<SmbShareConnector> { DefaultSmbShareConnector(androidContext()) }
    single { FileCacheRepository(androidContext()) }
    viewModel {
        ExplorerViewModel(
            appContext = androidContext(),
            folderStore = get(),
            thumbnailRepository = FileThumbnailRepository(androidContext()),
        )
    }
    viewModel {
        SmbExplorerViewModel(
            smbClient = get(),
            connectionStore = get(),
            shareConnector = get(),
            // サムネイル生成は SMBJ 固有の部分読み取りを使うため、ポート越しではなく実装型に落として渡す
            thumbnailRepositoryFactory = { provider -> SmbThumbnailRepository(androidContext()) { provider() as? SmbFileSource } },
            canWriteDownloads = FileManagerAccess::hasAllFilesAccess,
            progressThrottle = ProgressThrottle(intervalMs = PROGRESS_EMIT_INTERVAL_MS, now = SystemClock::elapsedRealtime),
        )
    }
}
