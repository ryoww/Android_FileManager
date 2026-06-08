package com.ryo.androidfilemanager.explorer

import com.ryo.androidfilemanager.data.model.FileItem

internal enum class FileSortOption(
    val label: String,
) {
    NAME("Name"),
    DATE("Date"),
    SIZE("Size"),
    ;

    companion object {
        val DEFAULT: FileSortOption = DATE
    }
}

internal fun List<FileItem>.sortedForDisplay(sortOption: FileSortOption): List<FileItem> =
    sortedWith(sortOption.comparator())

private fun FileSortOption.comparator(): Comparator<FileItem> {
    val directoryFirst = compareByDescending<FileItem> { it.isDirectory }
    return when (this) {
        FileSortOption.NAME -> directoryFirst.thenBy { it.name.lowercase() }
        FileSortOption.DATE -> directoryFirst
            .thenByDescending { it.modifiedAt ?: Long.MIN_VALUE }
            .thenBy { it.name.lowercase() }
        FileSortOption.SIZE -> directoryFirst
            .thenByDescending { it.size ?: Long.MIN_VALUE }
            .thenBy { it.name.lowercase() }
    }
}
