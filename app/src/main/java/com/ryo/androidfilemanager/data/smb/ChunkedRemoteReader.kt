package com.ryo.androidfilemanager.data.smb

import com.ryo.androidfilemanager.core.application.port.RemoteReadableFile

import java.io.IOException
import kotlinx.coroutines.runBlocking

/**
 * RemoteReadableFile をチャンク単位で読み取り、LRU でキャッシュする同期リーダー。
 *
 * PdfRenderer や MediaMetadataRetriever のような同期 API の読み取りコールバックから
 * 使うことを想定している。readBudgetBytes を超える転送は IOException で打ち切り、
 * 壊れたファイル等で際限なくダウンロードし続けるのを防ぐ。
 *
 * android.util.LruCache は JVM ユニットテストで使えないため、アクセス順の
 * LinkedHashMap による自前 LRU を使う(Android フレームワーク非依存)。
 */
internal class ChunkedRemoteReader(
    private val remote: RemoteReadableFile,
    private val readBudgetBytes: Long,
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
    private val maxCachedChunks: Int = DEFAULT_MAX_CACHED_CHUNKS,
    private val isCancelled: () -> Boolean = { false },
) {
    private val chunks = object : LinkedHashMap<Long, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean {
            return size > maxCachedChunks
        }
    }

    /** budget 超過時のエラーメッセージ用に、evict 済みも含めたユニークチャンク取得数を数える。 */
    private var uniqueChunksFetched: Int = 0

    @Volatile
    var fetchedBytes: Long = 0L
        private set

    /**
     * [position] から最大 [length] バイトを [dest] の [destOffset] 以降へ読み込む。
     * 読み込んだバイト数を返す(EOF 到達時は 0)。
     */
    @Synchronized
    fun read(position: Long, dest: ByteArray, destOffset: Int, length: Int): Int {
        if (isCancelled()) {
            throw IOException("Remote read cancelled.")
        }
        if (position >= remote.size) {
            return 0
        }

        val end = minOf(position + length, remote.size)
        var current = position
        var copied = 0
        while (current < end) {
            val chunkIndex = current / chunkSize
            val chunk = chunkAt(chunkIndex) ?: break
            val chunkOffset = (current - chunkIndex * chunkSize).toInt()
            if (chunkOffset >= chunk.size) {
                break
            }
            val toCopy = minOf((chunk.size - chunkOffset).toLong(), end - current).toInt()
            System.arraycopy(chunk, chunkOffset, dest, destOffset + copied, toCopy)
            copied += toCopy
            current += toCopy
        }
        return copied
    }

    private fun chunkAt(index: Long): ByteArray? {
        if (isCancelled()) {
            throw IOException("Remote read cancelled.")
        }
        chunks[index]?.let { return it }

        if (fetchedBytes >= readBudgetBytes) {
            throw IOException(
                "Remote read budget exceeded: fetchedBytes=$fetchedBytes / readBudgetBytes=$readBudgetBytes " +
                    "(uniqueChunksFetched=$uniqueChunksFetched, cachedChunks=${chunks.size})",
            )
        }

        val start = index * chunkSize
        val length = minOf(chunkSize, remote.size - start).toInt()
        if (length <= 0) {
            return null
        }

        val buffer = ByteArray(length)
        var totalRead = 0
        runBlocking {
            while (totalRead < length) {
                if (isCancelled()) {
                    throw IOException("Remote read cancelled.")
                }
                val read = remote.readAt(start + totalRead, buffer, totalRead, length - totalRead)
                if (read <= 0) {
                    break
                }
                totalRead += read
            }
        }
        if (totalRead < length) {
            throw IOException("Remote read returned ${totalRead} of $length bytes")
        }

        fetchedBytes += totalRead
        uniqueChunksFetched += 1
        chunks[index] = buffer
        return buffer
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE = 512L * 1024L
        const val DEFAULT_MAX_CACHED_CHUNKS = 48
    }
}
