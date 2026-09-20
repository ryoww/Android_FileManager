package com.ryo.androidfilemanager.data.smb

import com.ryo.androidfilemanager.core.application.port.RemoteReadableFile

import android.media.MediaDataSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

/**
 * RemoteReadableFile を MediaDataSource として見せる。
 *
 * MediaMetadataRetriever に渡すと、動画のメタデータ(moov)とフレームの
 * デコードに必要な範囲だけがオンデマンドで読み取られるため、
 * 巨大な動画でも全量ダウンロードなしでサムネイルを生成できる。
 */
class RemoteMediaDataSource(
    private val remote: RemoteReadableFile,
    readBudgetBytes: Long,
    chunkSize: Long = ChunkedRemoteReader.DEFAULT_CHUNK_SIZE,
    maxCachedChunks: Int = VIDEO_MAX_CACHED_CHUNKS,
) : MediaDataSource() {
    private val lifecycle = RemoteMediaDataSourceLifecycle()
    private val reader = ChunkedRemoteReader(
        remote = remote,
        readBudgetBytes = readBudgetBytes,
        chunkSize = chunkSize,
        maxCachedChunks = maxCachedChunks,
        isCancelled = { !lifecycle.canRead },
    )

    val fetchedBytes: Long
        get() = reader.fetchedBytes

    val isCancelled: Boolean
        get() = lifecycle.isCancelled

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (!lifecycle.canRead) {
            return -1
        }
        if (position >= remote.size) {
            return -1
        }

        val read = reader.read(position, buffer, offset, size)
        return if (read <= 0) -1 else read
    }

    override fun getSize(): Long = remote.size

    fun cancel() {
        if (lifecycle.cancel()) {
            runBlocking { remote.close() }
        }
    }

    override fun close() {
        if (lifecycle.close()) {
            runBlocking { remote.close() }
        }
    }

    companion object {
        // 動画は MP4 の moov 解析でシークが飛び回るため、キャッシュが小さいと
        // スラッシングし readBudget を無駄に消費する。48 チャンクまで保持する。
        const val VIDEO_MAX_CACHED_CHUNKS = 48
    }
}

internal class RemoteMediaDataSourceLifecycle {
    private val cancelled = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    val canRead: Boolean
        get() = !closed.get()

    val isCancelled: Boolean
        get() = cancelled.get()

    fun cancel(): Boolean {
        cancelled.set(true)
        return closed.compareAndSet(false, true)
    }

    fun close(): Boolean = closed.compareAndSet(false, true)
}
