package com.ryo.androidfilemanager.core.application.port

import com.ryo.androidfilemanager.core.domain.SmbConnectionInfo
import kotlinx.coroutines.flow.Flow

/** SMB 接続情報の永続化ポート。実装は DataStore などアダプタ側に置く。 */
interface SmbConnectionRepository {
    val savedConnection: Flow<SmbConnectionInfo?>

    suspend fun save(info: SmbConnectionInfo)
    suspend fun clear()
}
