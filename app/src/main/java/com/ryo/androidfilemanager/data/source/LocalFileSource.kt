package com.ryo.androidfilemanager.data.source

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.model.OpenedFile
import com.ryo.androidfilemanager.data.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalFileSource(
    context: Context,
    rootTreeUri: Uri,
) : FileSource {
    private val appContext = context.applicationContext
    private val documentsByPath = mutableMapOf<String, DocumentFile>()
    private val rootDocument: DocumentFile = requireNotNull(
        DocumentFile.fromTreeUri(appContext, rootTreeUri),
    ) {
        "Selected folder is no longer available. Choose the folder again."
    }

    val rootPath: String = rootDocument.uri.toString()
    val rootName: String = rootDocument.name ?: "Selected folder"

    init {
        require(rootDocument.isDirectory) {
            "Selected URI is not a directory. Choose a folder."
        }
        documentsByPath[rootPath] = rootDocument
    }

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val resolvedPath = path.ifBlank { rootPath }
        val directory = documentsByPath[resolvedPath]
            ?: throw IllegalArgumentException(
                "The selected directory cannot be resolved. Return to the root folder and try again.",
            )

        if (!directory.exists()) {
            throw IllegalStateException(
                "Local folder access failed. The folder may have been moved or permission may have expired.",
            )
        }

        if (!directory.isDirectory) {
            throw IllegalArgumentException("Selected item is not a folder: ${directory.name.orEmpty()}")
        }

        directory.listFiles()
            .onEach { child -> documentsByPath[child.uri.toString()] = child }
            .sortedWith(
                compareByDescending<DocumentFile> { it.isDirectory }
                    .thenBy { it.name?.lowercase().orEmpty() },
            )
            .map { child -> child.toFileItem() }
    }

    override suspend fun open(file: FileItem): OpenedFile {
        val rawUri = file.uri ?: file.path
        require(rawUri.isNotBlank()) {
            "Local file URI is missing. Choose the folder again and reload the listing."
        }

        return OpenedFile.Local(
            uri = Uri.parse(rawUri),
            viewerType = detectViewerType(file.name, file.mimeType),
        )
    }

    private fun DocumentFile.toFileItem(): FileItem {
        val childUri = uri.toString()
        return FileItem(
            name = name ?: "(unnamed)",
            path = childUri,
            uri = childUri,
            isDirectory = isDirectory,
            size = length()
                .takeIf { !isDirectory }
                ?.takeIf { it >= 0L },
            modifiedAt = lastModified().takeIf { it > 0L },
            mimeType = type,
            sourceType = SourceType.LOCAL,
        )
    }
}
