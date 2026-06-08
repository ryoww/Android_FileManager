package com.ryo.androidfilemanager.data.source

import android.net.Uri
import android.os.Environment
import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.OpenedFile
import com.ryo.androidfilemanager.data.model.SourceType
import java.io.File
import java.net.URLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExternalStorageFileSource(
    rootDirectory: File = Environment.getExternalStorageDirectory(),
    downloadsDirectoryName: String = Environment.DIRECTORY_DOWNLOADS ?: "Download",
) : FileSource {
    private val rootDirectory = rootDirectory.canonicalFile

    val rootPath: String = this.rootDirectory.path

    val downloadsPath: String = File(this.rootDirectory, downloadsDirectoryName)
        .canonicalFile
        .path

    val defaultStartPath: String
        get() = downloadsPath.takeIf { File(it).isDirectory } ?: rootPath

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val directory = resolveDirectory(path)
        val children = directory.listFiles()
            ?: throw IllegalStateException(
                "Storage folder could not be listed. Enable full storage access and try again.",
            )

        children
            .sortedWith(
                compareByDescending<File> { it.isDirectory }
                    .thenBy { it.name.lowercase() },
            )
            .map { child -> child.toFileItem() }
    }

    override suspend fun open(file: FileItem): OpenedFile {
        require(!file.isDirectory) {
            "Directory cannot be opened as a file."
        }

        val localFile = File(file.path)
        require(localFile.isFile) {
            "Local file is no longer available: ${file.name}"
        }

        return OpenedFile.Local(
            uri = Uri.fromFile(localFile),
            viewerType = detectViewerType(file.name, file.mimeType),
        )
    }

    fun displayName(path: String): String {
        val file = runCatching { File(path.ifBlank { rootPath }).canonicalFile }
            .getOrDefault(rootDirectory)

        return when (file.path) {
            rootPath -> "Internal Storage"
            downloadsPath -> "Download"
            else -> file.name.takeIf { it.isNotBlank() } ?: file.path
        }
    }

    fun navigateUpLabel(pathStack: List<String>): String {
        val parentPath = pathStack.dropLast(1).lastOrNull() ?: return "Parent Folder"
        return if (parentPath == rootPath) {
            "Internal Storage"
        } else {
            "Parent Folder"
        }
    }

    private fun resolveDirectory(path: String): File {
        val directory = File(path.ifBlank { rootPath }).canonicalFile
        require(directory.exists()) {
            "Storage folder does not exist: ${directory.path}"
        }
        require(directory.isDirectory) {
            "Selected path is not a folder: ${directory.path}"
        }
        return directory
    }

    private fun File.toFileItem(): FileItem = FileItem(
        name = name.takeIf { it.isNotBlank() } ?: path,
        path = path,
        uri = toURI().toString(),
        isDirectory = isDirectory,
        size = length().takeIf { isFile && it >= 0L },
        modifiedAt = lastModified().takeIf { it > 0L },
        mimeType = if (isDirectory) null else URLConnection.guessContentTypeFromName(name),
        sourceType = SourceType.LOCAL,
    )
}
