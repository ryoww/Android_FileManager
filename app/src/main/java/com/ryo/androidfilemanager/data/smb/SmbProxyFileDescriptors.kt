package com.ryo.androidfilemanager.data.smb

import com.ryo.androidfilemanager.core.application.port.RemoteReadableFile

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * RemoteReadableFile をシーク可能な実体の ParcelFileDescriptor として見せる。
 *
 * PdfRenderer のようにシーク可能 fd を要求する API へ、SMB 上の巨大ファイルを
 * 全量ダウンロードせずに渡すために使う。読み取りは ChunkedRemoteReader 経由。
 */
object SmbProxyFileDescriptors {
    // 計測用の一時タグ。ボトルネック特定が済んだら関連ログごと削除する
    const val PERF_TAG = SmbConnectionPool.PERF_TAG

    private val handlerThread by lazy {
        HandlerThread("SmbProxyFd").apply { start() }
    }
    private val handler by lazy { Handler(handlerThread.looper) }

    fun open(
        context: Context,
        remote: RemoteReadableFile,
        readBudgetBytes: Long,
        logLabel: String,
    ): ParcelFileDescriptor {
        val storageManager = context.getSystemService(StorageManager::class.java)
        val reader = ChunkedRemoteReader(remote, readBudgetBytes)
        return storageManager.openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_READ_ONLY,
            object : ProxyFileDescriptorCallback() {
                override fun onGetSize(): Long {
                    if (remote.size < 0) {
                        throw ErrnoException("fstat", OsConstants.EIO)
                    }
                    return remote.size
                }

                override fun onRead(offset: Long, size: Int, data: ByteArray): Int = try {
                    reader.read(offset, data, 0, size)
                } catch (errno: ErrnoException) {
                    throw errno
                } catch (throwable: Throwable) {
                    throw ErrnoException("read", OsConstants.EIO)
                }

                override fun onRelease() {
                    Log.d(
                        PERF_TAG,
                        "proxy release: $logLabel fetched ${reader.fetchedBytes / 1024}KB",
                    )
                    runBlocking { remote.close() }
                }
            },
            handler,
        )
    }
}
