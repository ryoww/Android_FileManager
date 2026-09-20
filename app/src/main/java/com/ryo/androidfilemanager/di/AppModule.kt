package com.ryo.androidfilemanager.di

import com.ryo.androidfilemanager.core.application.port.SmbClient
import com.ryo.androidfilemanager.core.application.port.SmbConnectionRepository
import com.ryo.androidfilemanager.data.cache.FileCacheRepository
import com.ryo.androidfilemanager.data.local.LocalFolderStore
import com.ryo.androidfilemanager.data.smb.DefaultSmbClient
import com.ryo.androidfilemanager.data.smb.SmbConnectionStore
import com.ryo.androidfilemanager.data.thumbnail.FileThumbnailRepository
import com.ryo.androidfilemanager.explorer.ExplorerViewModel
import com.ryo.androidfilemanager.smb.SmbExplorerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { LocalFolderStore(androidContext()) }
    single<SmbConnectionRepository> { SmbConnectionStore(androidContext()) }
    single<SmbClient> { DefaultSmbClient() }
    single { FileCacheRepository(androidContext()) }
    viewModel {
        ExplorerViewModel(
            appContext = androidContext(),
            folderStore = get(),
            thumbnailRepository = FileThumbnailRepository(androidContext()),
        )
    }
    viewModel { SmbExplorerViewModel(appContext = androidContext(), smbClient = get(), connectionStore = get()) }
}
