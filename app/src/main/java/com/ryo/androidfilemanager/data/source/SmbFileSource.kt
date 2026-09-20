package com.ryo.androidfilemanager.data.source

import com.ryo.androidfilemanager.core.application.port.FileSource

import com.ryo.androidfilemanager.core.domain.detectViewerType

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue.EnumUtils
import com.hierynomus.smbj.share.DiskShare
import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.core.domain.OpenedFile
import com.ryo.androidfilemanager.core.domain.SourceType
import com.ryo.androidfilemanager.core.domain.TransferKind
import com.ryo.androidfilemanager.core.domain.TransferProgress
import com.ryo.androidfilemanager.core.domain.ViewerType
import com.ryo.androidfilemanager.core.domain.SmbConnectionInfo
import com.ryo.androidfilemanager.data.smb.SmbConnectionPool
import com.ryo.androidfilemanager.data.smb.SmbReadableFile
import com.ryo.androidfilemanager.core.application.copyWithProgress
import com.ryo.androidfilemanager.data.smb.newSmbClient
import com.ryo.androidfilemanager.data.smb.toAuthenticationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLConnection
import java.security.MessageDigest
import java.util.EnumSet
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SmbDownloadSummary(
    val fileCount: Int,
    val destinationPath: String,
)

data class SmbUploadSummary(
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

        return SmbConnectionPool.useShare(connectionInfo) { share ->
            share.list(directoryPath.toSmbPath())
                .asSequence()
                .filterNot { it.fileName == "." || it.fileName == ".." }
                .filterNot { info -> isSmbThumbnailCacheDirectory(info.fileName) }
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

    override suspend fun open(
        file: FileItem,
        onProgress: ((TransferProgress) -> Unit)?,
    ): OpenedFile {
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
            uri = Uri.fromFile(cacheSmallFile(file, onProgress)).toString(),
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

        return cacheSmallFile(file, onProgress = null)
    }

    /**
     * ランダムアクセス可能なリモートファイルを開く。専用接続を1本張るため、
     * 呼び出し側は使用後に必ず close すること(プロキシfd経由なら onRelease で閉じる)。
     */
    suspend fun openRandomAccess(file: FileItem): SmbReadableFile {
        require(!file.isDirectory) {
            "SMB directory cannot be opened as a file."
        }
        return remoteReadableFile(file)
    }

    /**
     * プールされた接続上でランダムアクセス可能なリモートファイルを開く。
     * 接続確立コストを払わないためサムネイル生成のような短時間の用途向け。
     * close はファイルハンドルのみ閉じ、共有接続は維持される。
     * プールが切断された場合、以降の読み取りは失敗する(呼び出し側でリトライ判断)。
     */
    suspend fun openPooledRandomAccess(file: FileItem): SmbReadableFile {
        require(!file.isDirectory) {
            "SMB directory cannot be opened as a file."
        }

        return SmbConnectionPool.useShare(connectionInfo) { share ->
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
                    runCatching { remoteFile.close() }
                },
            )
        }
    }

    /**
     * ファイル先頭から最大 [maxBytes] バイトだけ読み取る。
     * EXIF 埋め込みサムネイル抽出など、全量ダウンロードを避けたい用途向け。
     */
    suspend fun readHeadBytes(file: FileItem, maxBytes: Int): ByteArray {
        require(!file.isDirectory) {
            "SMB directory cannot be read as a file."
        }

        val startedAt = SystemClock.elapsedRealtime()
        return SmbConnectionPool.useShare(connectionInfo) { share ->
            share.openFile(
                file.path.toSmbPath(),
                READ_ACCESS,
                FILE_ATTRIBUTES,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                READ_OPTIONS,
            ).use { remoteFile ->
                val targetSize = file.size
                    ?.coerceAtMost(maxBytes.toLong())
                    ?.toInt()
                    ?: maxBytes
                val buffer = ByteArray(targetSize)
                var totalRead = 0
                while (totalRead < buffer.size) {
                    val read = remoteFile.read(
                        buffer,
                        totalRead.toLong(),
                        totalRead,
                        buffer.size - totalRead,
                    )
                    if (read <= 0) {
                        break
                    }
                    totalRead += read
                }
                Log.d(
                    SmbConnectionPool.PERF_TAG,
                    "head read: ${file.name} ${totalRead / 1024}KB " +
                        "in ${SystemClock.elapsedRealtime() - startedAt}ms",
                )
                if (totalRead == buffer.size) buffer else buffer.copyOf(totalRead)
            }
        }
    }

    suspend fun copyFreshThumbnailSidecar(
        file: FileItem,
        destination: File,
    ): Boolean = withContext(Dispatchers.IO) {
        val sidecarPath = smbThumbnailSidecarPath(file.path, file.name)
        SmbConnectionPool.useShare(connectionInfo) { share ->
            val smbPath = sidecarPath.toSmbPath()
            if (!share.fileExists(smbPath)) {
                return@useShare false
            }

            val information = share.getFileInformation(smbPath)
            val sidecarSize = information.standardInformation.endOfFile
            val sidecarModifiedAt = information.basicInformation.lastWriteTime?.toEpochMillis()
            if (sidecarSize <= 0L || sidecarSize > MAX_THUMBNAIL_SIDECAR_BYTES) {
                return@useShare false
            }
            if (!isFreshSmbThumbnailSidecar(file.modifiedAt, sidecarModifiedAt)) {
                return@useShare false
            }

            destination.parentFile?.mkdirs()
            val tempFile = File.createTempFile("${destination.name}.", ".tmp", destination.parentFile)
            try {
                share.openFile(
                    smbPath,
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
                if (tempFile.length() != sidecarSize) {
                    return@useShare false
                }
                tempFile.copyTo(destination, overwrite = true)
                true
            } finally {
                tempFile.delete()
            }
        }
    }

    suspend fun storeThumbnailSidecar(
        file: FileItem,
        thumbnailFile: File,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!thumbnailFile.isFile || thumbnailFile.length() <= 0L) {
            return@withContext false
        }

        val sidecarPath = smbThumbnailSidecarPath(file.path, file.name)
        val sidecarDirectory = sidecarPath.substringBeforeLast('/', missingDelimiterValue = "")
        SmbConnectionPool.useShare(connectionInfo) { share ->
            val directorySmbPath = sidecarDirectory.toSmbPath()
            if (!share.folderExists(directorySmbPath)) {
                runCatching { share.mkdir(directorySmbPath) }
                if (!share.folderExists(directorySmbPath)) {
                    return@useShare false
                }
            }

            share.openFile(
                sidecarPath.toSmbPath(),
                WRITE_ACCESS,
                FILE_ATTRIBUTES,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                WRITE_OPTIONS,
            ).use { remoteFile ->
                thumbnailFile.inputStream().use { input ->
                    val buffer = ByteArray(THUMBNAIL_SIDECAR_BUFFER_BYTES)
                    var position = 0L
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) {
                            break
                        }
                        val bytesWritten = remoteFile.write(buffer, position, 0, bytesRead)
                        if (bytesWritten != bytesRead.toLong()) {
                            throw IOException("SMB thumbnail sidecar was not fully written.")
                        }
                        position += bytesWritten
                    }
                }
            }
            true
        }
    }

    suspend fun downloadToDownloads(
        files: List<FileItem>,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): SmbDownloadSummary = withContext(Dispatchers.IO) {
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

        // トップレベル選択にディレクトリが含まれると再帰分の件数が事前に分からないため totalFiles は null にする
        val totalFiles = files.size.takeIf { files.none { file -> file.isDirectory } }
        var downloadedFileCount = 0
        SmbConnectionPool.useShare(connectionInfo) { share ->
            files.forEach { file ->
                downloadedFileCount += share.downloadItem(
                    remotePath = file.path,
                    name = file.name,
                    isDirectory = file.isDirectory,
                    destinationParent = destinationRoot,
                    completedFiles = downloadedFileCount,
                    totalFiles = totalFiles,
                    onProgress = onProgress,
                )
            }
        }

        SmbDownloadSummary(
            fileCount = downloadedFileCount,
            destinationPath = destinationRoot.path,
        )
    }

    suspend fun uploadFromUris(
        uris: List<Uri>,
        remoteDirectoryPath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): SmbUploadSummary = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty()) {
            "Select one or more files to upload."
        }

        val destinationDirectory = remoteDirectoryPath.normalizeRemotePath()
        var uploadedFileCount = 0
        SmbConnectionPool.useShare(connectionInfo) { share ->
            val reservedNames = share.list(destinationDirectory.toSmbPath())
                .asSequence()
                .filterNot { it.fileName == "." || it.fileName == ".." }
                .map { it.fileName.lowercase(Locale.ROOT) }
                .toMutableSet()

            uris.forEach { uri ->
                val uploadName = reserveSmbUploadFileName(displayNameForUri(uri), reservedNames)
                val remotePath = destinationDirectory.joinRemotePath(uploadName)
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("The selected file could not be opened for upload.")
                val totalBytes = uriContentLength(uri)
                val completedFilesSoFar = uploadedFileCount

                input.use { stream ->
                    share.openFile(
                        remotePath.toSmbPath(),
                        WRITE_ACCESS,
                        FILE_ATTRIBUTES,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_CREATE,
                        WRITE_OPTIONS,
                    ).use { remoteFile ->
                        val buffer = ByteArray(UPLOAD_BUFFER_BYTES)
                        var position = 0L
                        while (true) {
                            val bytesRead = stream.read(buffer)
                            if (bytesRead < 0) {
                                break
                            }
                            val bytesWritten = remoteFile.write(buffer, position, 0, bytesRead)
                            if (bytesWritten != bytesRead.toLong()) {
                                throw IOException("SMB upload stopped before the file was fully written.")
                            }
                            position += bytesWritten
                            onProgress?.invoke(
                                TransferProgress(
                                    kind = TransferKind.UPLOAD,
                                    fileName = uploadName,
                                    bytesTransferred = position,
                                    totalBytes = totalBytes,
                                    completedFiles = completedFilesSoFar,
                                    totalFiles = uris.size,
                                ),
                            )
                        }
                    }
                }
                uploadedFileCount += 1
            }
        }

        SmbUploadSummary(
            fileCount = uploadedFileCount,
            destinationPath = destinationDirectory.ifBlank { "Share Root" },
        )
    }

    private fun uriContentLength(uri: Uri): Long? = runCatching {
        appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.length.takeIf { it != android.content.res.AssetFileDescriptor.UNKNOWN_LENGTH }
        }
    }.getOrNull()

    private suspend fun cacheSmallFile(
        file: FileItem,
        onProgress: ((TransferProgress) -> Unit)?,
    ): File = withContext(Dispatchers.IO) {
        val cacheFile = File(smbCacheDir, "${file.cacheKey()}.${file.name.safeFileName()}")
        if (cacheFile.exists() && cacheFile.length() == file.size) {
            Log.d(SmbConnectionPool.PERF_TAG, "smb_cache hit: ${file.name}")
            return@withContext cacheFile
        }

        val startedAt = SystemClock.elapsedRealtime()
        smbCacheDir.mkdirs()
        // ダウンロードは並行に走り得るため、一時ファイル名は呼び出しごとに一意にする
        val tempFile = File.createTempFile("${cacheFile.name}.", ".tmp", smbCacheDir)
        SmbConnectionPool.useShare(connectionInfo) { share ->
            share.openFile(
                file.path.toSmbPath(),
                READ_ACCESS,
                FILE_ATTRIBUTES,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                READ_OPTIONS,
            ).use { remoteFile ->
                val totalBytes = file.size?.takeIf { it >= 0L }
                    ?: runCatching {
                        remoteFile.fileInformation.standardInformation.endOfFile
                    }.getOrNull()
                FileOutputStream(tempFile).use { output ->
                    copyWithProgress(
                        reader = { position, buffer, offset, length ->
                            remoteFile.read(buffer, position, offset, length)
                        },
                        output = output,
                        totalBytes = totalBytes,
                        onProgress = { copied ->
                            onProgress?.invoke(
                                TransferProgress(
                                    kind = TransferKind.DOWNLOAD,
                                    fileName = file.name,
                                    bytesTransferred = copied,
                                    totalBytes = totalBytes,
                                ),
                            )
                        },
                    )
                }
            }
        }

        if (cacheFile.exists()) {
            cacheFile.delete()
        }
        tempFile.renameTo(cacheFile)
        Log.d(
            SmbConnectionPool.PERF_TAG,
            "full download: ${file.name} ${cacheFile.length() / 1024}KB " +
                "in ${SystemClock.elapsedRealtime() - startedAt}ms",
        )
        cacheFile
    }

    private suspend fun remoteReadableFile(file: FileItem): SmbReadableFile = withContext(Dispatchers.IO) {
        val client = newSmbClient()
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
        completedFiles: Int,
        totalFiles: Int?,
        onProgress: ((TransferProgress) -> Unit)?,
    ): Int {
        if (isDirectory) {
            val destinationDirectory = uniqueDirectory(File(destinationParent, name.safeFileName()))
            destinationDirectory.mkdirs()

            var fileCount = 0
            list(remotePath.toSmbPath())
                .asSequence()
                .filterNot { it.fileName == "." || it.fileName == ".." }
                .filterNot { info -> isSmbThumbnailCacheDirectory(info.fileName) }
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
                        completedFiles = completedFiles + fileCount,
                        totalFiles = totalFiles,
                        onProgress = onProgress,
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
            val totalBytes = runCatching {
                remoteFile.fileInformation.standardInformation.endOfFile
            }.getOrNull()
            FileOutputStream(destinationFile).use { output ->
                copyWithProgress(
                    reader = { position, buffer, offset, length ->
                        remoteFile.read(buffer, position, offset, length)
                    },
                    output = output,
                    totalBytes = totalBytes,
                    onProgress = { copied ->
                        onProgress?.invoke(
                            TransferProgress(
                                kind = TransferKind.DOWNLOAD,
                                fileName = name,
                                bytesTransferred = copied,
                                totalBytes = totalBytes,
                                completedFiles = completedFiles,
                                totalFiles = totalFiles,
                            ),
                        )
                    },
                )
            }
        }
        return 1
    }

    private fun displayNameForUri(uri: Uri): String {
        return appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)
            } else {
                null
            }
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "upload"
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
        val WRITE_ACCESS: Set<AccessMask> = EnumSet.of(
            AccessMask.GENERIC_WRITE,
            AccessMask.FILE_WRITE_DATA,
            AccessMask.FILE_WRITE_ATTRIBUTES,
        )
        val WRITE_OPTIONS: Set<SMB2CreateOptions> = EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        const val UPLOAD_BUFFER_BYTES = 256 * 1024
        const val THUMBNAIL_SIDECAR_BUFFER_BYTES = 64 * 1024
        const val MAX_THUMBNAIL_SIDECAR_BYTES = 5L * 1024L * 1024L
    }
}

private const val SMB_THUMBNAIL_CACHE_DIRECTORY = ".thumbnails"

internal fun isSmbThumbnailCacheDirectory(fileName: String): Boolean =
    fileName.equals(SMB_THUMBNAIL_CACHE_DIRECTORY, ignoreCase = true)

internal fun smbThumbnailSidecarPath(
    filePath: String,
    fileName: String,
): String {
    val normalizedPath = filePath.trim().replace('\\', '/').trim('/')
    val parentPath = normalizedPath.substringBeforeLast('/', missingDelimiterValue = "")
    return listOf(parentPath, SMB_THUMBNAIL_CACHE_DIRECTORY, "$fileName.jpg")
        .filter { segment -> segment.isNotBlank() }
        .joinToString("/")
}

internal fun isFreshSmbThumbnailSidecar(
    videoModifiedAt: Long?,
    sidecarModifiedAt: Long?,
): Boolean = when {
    sidecarModifiedAt == null -> false
    videoModifiedAt == null -> true
    else -> sidecarModifiedAt >= videoModifiedAt
}

internal fun reserveSmbUploadFileName(
    rawName: String,
    reservedNames: MutableSet<String>,
): String {
    val normalized = rawName
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
        .trimEnd('.')
        .ifBlank { "upload" }
    val extension = normalized.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { normalized.indexOf('.') > 0 }
        ?.let { ".$it" }
        .orEmpty()
    val baseName = normalized.removeSuffix(extension)

    var index = 1
    while (true) {
        val candidate = if (index == 1) {
            normalized
        } else {
            "$baseName ($index)$extension"
        }
        if (reservedNames.add(candidate.lowercase(Locale.ROOT))) {
            return candidate
        }
        index += 1
    }
}
