package com.ryo.androidfilemanager.core.application.port

import com.ryo.androidfilemanager.core.domain.SmbConnectionInfo

/** 接続済み共有への操作。一覧・開く（FileSource）と転送（FileTransfer）をまとめる */
interface SmbShareAccess : FileSource, FileTransfer

interface SmbShareConnector {
    fun connect(info: SmbConnectionInfo): SmbShareAccess

    /** 保持している接続をすべて閉じる。非 suspend（ViewModel の onCleared から呼ぶため）。実装は非同期に閉じてよい */
    fun disconnectAll()
}
