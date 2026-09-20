package com.ryo.androidfilemanager.core.application

import java.io.OutputStream

const val DEFAULT_TRANSFER_CHUNK_BYTES: Int = 256 * 1024

/** 位置指定読み取り。戻り値は読み取ったバイト数。EOF なら -1 または 0。 */
fun interface PositionedReader {
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

/**
 * reader から output へチャンク単位でコピーし、チャンクごとに累計バイト数を onProgress へ通知する。
 * SMBJ の File.read(byte[], long, int, int) をそのまま reader として渡せる形にすることで、
 * cacheSmallFile / downloadItem / uploadFromUris の進捗計測ロジックを一本化する。
 */
fun copyWithProgress(
    reader: PositionedReader,
    output: OutputStream,
    totalBytes: Long?,
    chunkSize: Int = DEFAULT_TRANSFER_CHUNK_BYTES,
    onProgress: (bytesCopied: Long) -> Unit,
): Long {
    require(chunkSize > 0) { "chunkSize must be positive." }

    val buffer = ByteArray(chunkSize)
    var position = 0L

    while (true) {
        val remaining = totalBytes?.let { it - position }
        if (remaining != null && remaining <= 0L) {
            break
        }
        val requestLength = remaining?.coerceAtMost(chunkSize.toLong())?.toInt() ?: chunkSize

        val bytesRead = reader.read(position, buffer, 0, requestLength)
        if (bytesRead <= 0) {
            break
        }

        output.write(buffer, 0, bytesRead)
        position += bytesRead
        onProgress(position)
    }

    if (position == 0L) {
        onProgress(0L)
    }

    return position
}
