package com.ryo.androidfilemanager.core.application

import com.ryo.androidfilemanager.core.application.port.FileSource
import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.OpenedFile
import com.ryo.androidfilemanager.core.domain.TransferProgress
import com.ryo.androidfilemanager.core.domain.withNameFallback

/** ファイルを開くユースケース。FileSource が名前を埋めない場合に FileItem.name で補完する。 */
class OpenEntryUseCase(private val fileSource: FileSource) {
    suspend operator fun invoke(
        entry: FileItem,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): OpenedFile {
        require(!entry.isDirectory) { "Cannot open a directory: ${entry.path}" }

        val opened = fileSource.open(entry, onProgress)
        return opened.withNameFallback(entry.name)
    }
}
