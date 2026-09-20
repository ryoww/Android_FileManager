package com.ryo.androidfilemanager

import android.app.Application
import com.ryo.androidfilemanager.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AndroidFileManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AndroidFileManagerApplication)
            modules(appModule)
        }
    }
}
