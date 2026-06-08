package com.ryo.androidfilemanager.data.source

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue.EnumUtils
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.share.DiskShare
import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.OpenedFile
import com.ryo.androidfilemanager.data.model.SourceType
import com.ryo.androidfilemanager.data.model.ViewerType
import com.ryo.androidfilemanager.data.smb.SmbConnectionInfo
import com.ryo.androidfilemanager.data.smb.SmbReadableFile
import com.ryo.androidfilemanager.data.smb.toAuthenticationContext
import com.ryo.androidfilemanager.data.smb.withDiskShare
import java.io.File
import java.io.FileOutputStream
import java.net.URLConnection
import java.security.MessageDigest
import java.util.EnumSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SmbDownloadSummary(
    val fileCount: Int,
    val destinationPath: String,
)

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
                name = file.name,
                mimeType = file.mimeType,
                viewerType = viewerType,
            )
        }

        return OpenedFile.Local(
            uri = Uri.fromFile(cacheSmallFile(file)),
            viewerType = viewerType,
        )
    }

    suspend fun cachePreviewFile(file: FileItem): File {
        require(!file.isDirectory) {
            "SMB directory cannot be cached as a preview."
        }

        val viewerType = detectViewerType(file.name, file.mimeType)
        require(viewerType != ViewerType.Video && viewerType != ViewerType.Audio) {
            "SMB media previews are not cached to avoid downloading large files."
        }

        return cacheSmallFile(file)
    }

    suspend fun downloadToDownloads(files: List<FileItem>): SmbDownloadSummary = withContext(Dispatchers.IO) {
        require(files.isNotEmpty()) {
            "Select one or more SMB files to download."
        }

        val downloadsDirectory = File(
            Environment.getExternalStorageDirectory(),
            Environment.DIRECTORY_DOWNLOADS,
        )
        val destinationRoot = File(
            File(downloadsDirectory, "AndroidFileManager SMB"),
            "${connectionInfo.host.safeFileName()}_${connectionInfo.shareName.safeFileName()}",
        )
        destinationRoot.mkdirs()

        var downloadedFileCount = 0
        withDiskShare(connectionInfo) { share ->
            files.forEach { file ->
                downloadedFileCount += share.downloadItem(
                    remotePath = file.path,
                    name = file.name,
                    isDirectory = file.isDirectory,
                    destinationParent = destinationRoot,
                )
            }
        }

        SmbDownloadSummary(
            fileCount = downloadedFileCount,
            destinationPath = destinationRoot.path,
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

    private suspend fun remoteReadableFile(file: FileItem): SmbReadableFile = withContext(Dispatchers.IO) {
        val client = SMBClient()
        val connection = client.connect(connectionInfo.host, connectionInfo.port)
        val session = connection.authenticate(connectionInfo.toAuthenticationContext())
        val share = session.connectShare(connectionInfo.shareName)

        require(share is DiskShare) {
            closeQuietly(share, session, connection, client)
            "SMB share '${connectionInfo.shareName}' is not a disk share."
        }

        val smbPath = file.path.toSmbPath()
        val resolvedSize = file.size?.takeIf { it > 0L }
            ?: runCatching {
                share.getFileInformation(smbPath).standardInformation.endOfFile
            }.getOrDefault(-1L)

        val remoteFile = share.openFile(
            smbPath,
            READ_ACCESS,
            FILE_ATTRIBUTES,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            READ_OPTIONS,
        )

        SmbReadableFile(
            size = resolvedSize,
            readBlock = { position, buffer, offset, length ->
                remoteFile.read(buffer, position, offset, length)
            },
            closeBlock = {
                closeQuietly(remoteFile, share, session, connection, client)
            },
        )
    }

    private fun DiskShare.downloadItem(
        remotePath: String,
        name: String,
        isDirectory: Boolean,
        destinationParent: File,
    ): Int {
        if (isDirectory) {
            val destinationDirectory = uniqueDirectory(File(destinationParent, name.safeFileName()))
            destinationDirectory.mkdirs()

            var fileCount = 0
            list(remotePath.toSmbPath())
                .asSequence()
                .filterNot { it.fileName == "." || it.fileName == ".." }
                .forEach { info ->
                    val childIsDirectory = EnumUtils.isSet(
                        info.fileAttributes,
                        FileAttributes.FILE_ATTRIBUTE_DIRECTORY,
                    )
                    fileCount += downloadItem(
                        remotePath = remotePath
                            .normalizeRemotePath()
                            .joinRemotePath(info.fileName),
                        name = info.fileName,
                        isDirectory = childIsDirectory,
                        destinationParent = destinationDirectory,
                    )
                }

            return fileCount
        }

        val destinationFile = uniqueFile(File(destinationParent, name.safeFileName()))
        destinationParent.mkdirs()
        openFile(
            remotePath.toSmbPath(),
            READ_ACCESS,
            FILE_ATTRIBUTES,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            READ_OPTIONS,
        ).use { remoteFile ->
            FileOutputStream(destinationFile).use { output ->
                remoteFile.read(output)
            }
        }
        return 1
    }

    private fun closeQuietly(vararg closeables: AutoCloseable) {
        closeables.forEach { closeable ->
            runCatching {
                closeable.close()
            }
        }
    }

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
        .ifBlank { "download" }

    private fun uniqueDirectory(candidate: File): File {
        if (!candidate.exists()) {
            return candidate
        }

        for (index in 1..9999) {
            val next = File(candidate.parentFile, "${candidate.name} ($index)")
            if (!next.exists()) {
                return next
            }
        }

        return File(candidate.parentFile, "${candidate.name} (${System.currentTimeMillis()})")
    }

    private fun uniqueFile(candidate: File): File {
        if (!candidate.exists()) {
            return candidate
        }

        val extension = candidate.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        val baseName = candidate.name.removeSuffix(extension)

        for (index in 1..9999) {
            val next = File(candidate.parentFile, "$baseName ($index)$extension")
            if (!next.exists()) {
                return next
            }
        }

        return File(candidate.parentFile, "$baseName (${System.currentTimeMillis()})$extension")
    }

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
