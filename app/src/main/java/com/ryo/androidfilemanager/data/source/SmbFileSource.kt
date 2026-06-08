package com.ryo.androidfilemanager.data.source

import android.content.Context
import android.net.Uri
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue.EnumUtils
import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.OpenedFile
import com.ryo.androidfilemanager.data.model.SourceType
import com.ryo.androidfilemanager.data.model.ViewerType
import com.ryo.androidfilemanager.data.smb.SmbConnectionInfo
import com.ryo.androidfilemanager.data.smb.SmbReadableFile
import com.ryo.androidfilemanager.data.smb.withDiskShare
import java.io.File
import java.io.FileOutputStream
import java.net.URLConnection
import java.security.MessageDigest
import java.util.EnumSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmbFileSource(
    context: Context,
    private val connectionInfo: SmbConnectionInfo,
) : FileSource {
    private val appContext = context.applicationContext
    private val smbCacheDir = File(appContext.cacheDir, "smb_cache")

    override suspend fun list(path: String): List<FileItem> {
        val directoryPath = path.normalizeRemotePath()

        return withDiskShare(connectionInfo) { share ->
            share.list(directoryPath.toSmbPath())
                .asSequence()
                .filterNot { it.fileName == "." || it.fileName == ".." }
                .map { info ->
                    val isDirectory = EnumUtils.isSet(
                        info.fileAttributes,
                        FileAttributes.FILE_ATTRIBUTE_DIRECTORY,
                    )
                    val childPath = directoryPath.joinRemotePath(info.fileName)

                    FileItem(
                        name = info.fileName,
                        path = childPath,
                        uri = null,
                        isDirectory = isDirectory,
                        size = if (isDirectory) null else info.endOfFile,
                        modifiedAt = info.lastWriteTime?.toEpochMillis(),
                        mimeType = URLConnection.guessContentTypeFromName(info.fileName),
                        sourceType = SourceType.SMB,
                    )
                }
                .sortedWith(
                    compareByDescending<FileItem> { it.isDirectory }
                        .thenBy { it.name.lowercase() },
                )
                .toList()
        }
    }

    override suspend fun open(file: FileItem): OpenedFile {
        require(!file.isDirectory) {
            "SMB directory cannot be opened as a file."
        }

        val viewerType = detectViewerType(file.name, file.mimeType)
        if (viewerType == ViewerType.Video || viewerType == ViewerType.Audio) {
            return OpenedFile.Stream(
                remoteFile = remoteReadableFile(file),
                viewerType = viewerType,
            )
        }

        return OpenedFile.Local(
            uri = Uri.fromFile(cacheSmallFile(file)),
            viewerType = viewerType,
        )
    }

    private suspend fun cacheSmallFile(file: FileItem): File = withContext(Dispatchers.IO) {
        val cacheFile = File(smbCacheDir, "${file.cacheKey()}.${file.name.safeFileName()}")
        if (cacheFile.exists() && cacheFile.length() == file.size) {
            return@withContext cacheFile
        }

        smbCacheDir.mkdirs()
        val tempFile = File(smbCacheDir, "${cacheFile.name}.tmp")
        withDiskShare(connectionInfo) { share ->
            share.openFile(
                file.path.toSmbPath(),
                READ_ACCESS,
                FILE_ATTRIBUTES,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                READ_OPTIONS,
            ).use { remoteFile ->
                FileOutputStream(tempFile).use { output ->
                    remoteFile.read(output)
                }
            }
        }

        if (cacheFile.exists()) {
            cacheFile.delete()
        }
        tempFile.renameTo(cacheFile)
        cacheFile
    }

    private fun remoteReadableFile(file: FileItem): SmbReadableFile = SmbReadableFile(
        size = file.size ?: 0L,
        readBlock = { position, buffer, offset, length ->
            withDiskShare(connectionInfo) { share ->
                share.openFile(
                    file.path.toSmbPath(),
                    READ_ACCESS,
                    FILE_ATTRIBUTES,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    READ_OPTIONS,
                ).use { remoteFile ->
                    remoteFile.read(buffer, position, offset, length)
                }
            }
        },
        closeBlock = {},
    )

    private fun FileItem.cacheKey(): String {
        val raw = listOf(
            connectionInfo.host,
            connectionInfo.shareName,
            path,
            size?.toString().orEmpty(),
            modifiedAt?.toString().orEmpty(),
        ).joinToString(separator = ":")
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun String.normalizeRemotePath(): String = trim().replace('\\', '/').trim('/')

    private fun String.toSmbPath(): String = replace('/', '\\')

    private fun String.joinRemotePath(childName: String): String = if (isBlank()) {
        childName
    } else {
        "$this/$childName"
    }

    private fun String.safeFileName(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")

    private companion object {
        val READ_ACCESS: Set<AccessMask> = EnumSet.of(
            AccessMask.GENERIC_READ,
            AccessMask.FILE_READ_DATA,
            AccessMask.FILE_READ_ATTRIBUTES,
        )
        val FILE_ATTRIBUTES: Set<FileAttributes> = EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL)
        val READ_OPTIONS: Set<SMB2CreateOptions> = EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
    }
}
