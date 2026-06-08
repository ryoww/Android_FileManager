package com.ryo.androidfilemanager.data.smb

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@OptIn(UnstableApi::class)
class SmbDataSource(
    private val remoteFile: RemoteReadableFile,
) : DataSource {
    private val transferListeners = mutableListOf<TransferListener>()
    private var openedDataSpec: DataSpec? = null
    private var openedUri: Uri? = null
    private var readPosition: Long = 0L
    private var bytesRemaining: Long = 0L
    private var opened = false

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val knownSize = remoteFile.size >= 0L
        if (knownSize && dataSpec.position > remoteFile.size) {
            throw IOException("SMB stream position is beyond file size.")
        }

        openedDataSpec = dataSpec
        openedUri = dataSpec.uri
        readPosition = dataSpec.position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            if (knownSize) {
                remoteFile.size - dataSpec.position
            } else {
                C.LENGTH_UNSET.toLong()
            }
        } else {
            dataSpec.length
        }

        transferListeners.forEach { listener ->
            listener.onTransferInitializing(this, dataSpec, false)
        }
        opened = true
        transferListeners.forEach { listener ->
            listener.onTransferStart(this, dataSpec, false)
        }

        return bytesRemaining
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }

        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }
        val bytesRead = runBlocking(Dispatchers.IO) {
            remoteFile.readAt(readPosition, buffer, offset, bytesToRead)
        }

        if (bytesRead <= 0) {
            return C.RESULT_END_OF_INPUT
        }

        readPosition += bytesRead
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }

        openedDataSpec?.let { dataSpec ->
            transferListeners.forEach { listener ->
                listener.onBytesTransferred(this, dataSpec, false, bytesRead)
            }
        }

        return bytesRead
    }

    override fun getUri(): Uri? = openedUri

    @Throws(IOException::class)
    override fun close() {
        val dataSpec = openedDataSpec
        openedUri = null
        openedDataSpec = null
        bytesRemaining = 0L

        if (opened && dataSpec != null) {
            transferListeners.forEach { listener ->
                listener.onTransferEnd(this, dataSpec, false)
            }
        }
        opened = false
    }

    class Factory(
        private val remoteFile: RemoteReadableFile,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(remoteFile)
    }
}
