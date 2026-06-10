package com.ryo.androidfilemanager.data.smb

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 接続情報単位で SMB セッションと DiskShare を再利用するプール。
 *
 * 接続確立(TCP + ネゴシエーション + 認証 + シェア接続)はファイル読み取りより
 * 桁違いに高コストなため、サムネイル一覧のような小さな読み取りの多発時は
 * 使い捨て接続にしないこと。smbj の DiskShare は同一セッション上での
 * 並行リードに対応しているため、複数コルーチンから同時に使ってよい。
 */
object SmbConnectionPool {
    private val lock = Any()
    private var pooled: PooledShare? = null

    suspend fun <T> useShare(
        info: SmbConnectionInfo,
        block: (DiskShare) -> T,
    ): T = withContext(Dispatchers.IO) {
        val share = acquire(info)
        try {
            block(share)
        } catch (throwable: Throwable) {
            if (!invalidateIfDisconnected(info)) {
                throw throwable
            }
            block(acquire(info))
        }
    }

    fun closeAll() {
        synchronized(lock) {
            pooled?.closeQuietly()
            pooled = null
        }
    }

    private fun acquire(info: SmbConnectionInfo): DiskShare = synchronized(lock) {
        val current = pooled
        if (current != null && current.info == info && current.share.isConnected) {
            return@synchronized current.share
        }

        current?.closeQuietly()
        pooled = null

        val client = SMBClient()
        val connection = client.connect(info.host, info.port)
        val session = connection.authenticate(info.toAuthenticationContext())
        val share = session.connectShare(info.shareName)
        if (share !is DiskShare) {
            runCatching { share.close() }
            runCatching { session.close() }
            runCatching { connection.close() }
            runCatching { client.close() }
            throw IllegalArgumentException("SMB share '${info.shareName}' is not a disk share.")
        }

        pooled = PooledShare(info, client, connection, session, share)
        share
    }

    private fun invalidateIfDisconnected(info: SmbConnectionInfo): Boolean = synchronized(lock) {
        val current = pooled ?: return@synchronized false
        if (current.info != info || current.share.isConnected) {
            return@synchronized false
        }

        current.closeQuietly()
        pooled = null
        true
    }

    private class PooledShare(
        val info: SmbConnectionInfo,
        private val client: SMBClient,
        private val connection: Connection,
        private val session: Session,
        val share: DiskShare,
    ) {
        fun closeQuietly() {
            runCatching { share.close() }
            runCatching { session.close() }
            runCatching { connection.close() }
            runCatching { client.close() }
        }
    }
}
