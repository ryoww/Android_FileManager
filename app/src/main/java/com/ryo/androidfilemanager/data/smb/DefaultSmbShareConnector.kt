package com.ryo.androidfilemanager.data.smb

import android.content.Context
import com.ryo.androidfilemanager.core.application.port.SmbShareAccess
import com.ryo.androidfilemanager.core.application.port.SmbShareConnector
import com.ryo.androidfilemanager.core.domain.SmbConnectionInfo
import com.ryo.androidfilemanager.data.source.SmbFileSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DefaultSmbShareConnector(context: Context) : SmbShareConnector {
    private val appContext = context.applicationContext

    // 切断はネットワーク I/O を伴うので専用スコープで非同期に閉じる。
    // Why not viewModelScope: onCleared 時には既にキャンセルされていて launch が動かない
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun connect(info: SmbConnectionInfo): SmbShareAccess = SmbFileSource(appContext, info)

    override fun disconnectAll() {
        closeScope.launch { SmbConnectionPool.closeAll() }
    }
}
