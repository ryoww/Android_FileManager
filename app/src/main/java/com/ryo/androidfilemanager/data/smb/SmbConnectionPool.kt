package com.ryo.androidfilemanager.data.smb

import com.ryo.androidfilemanager.core.domain.SmbConnectionInfo

import android.os.SystemClock
import android.util.Log
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
    // 計測用の一時タグ。ボトルネック特定が済んだら関連ログごと削除する
    const val PERF_TAG = "ThumbPerf"

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

        val startedAt = SystemClock.elapsedRealtime()
        val client = newSmbClient()
        val connection = client.connect(info.host, info.port)
        val session = connection.authenticate(info.toAuthenticationContext())
        val share = session.connectShare(info.shareName)
        Log.d(PERF_TAG, "pool miss: connect+auth+share took ${SystemClock.elapsedRealtime() - startedAt}ms")
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
