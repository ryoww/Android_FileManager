package com.ryo.androidfilemanager.core.domain

/** 任意位置から読める遠隔ファイル。OpenedFile.Stream が保持するのでドメインに置く（実装は SMB アダプタ） */
interface RemoteReadableFile {
    val size: Long

    suspend fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int

    suspend fun close()
}

