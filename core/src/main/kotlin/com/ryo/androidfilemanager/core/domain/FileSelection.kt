package com.ryo.androidfilemanager.core.domain

/** 複数選択モードの状態。paths が空なら非アクティブ扱い。 */
data class FileSelection(val paths: Set<String> = emptySet()) {
    val isActive: Boolean
        get() = paths.isNotEmpty()

    val count: Int
        get() = paths.size

    operator fun contains(path: String): Boolean = path in paths

    fun toggle(path: String): FileSelection =
        if (path in paths) FileSelection(paths - path) else FileSelection(paths + path)

    fun clear(): FileSelection = FileSelection()

    // entries 側の順序を基準にする（Set は順序を保証しないため）
    fun selectedFrom(entries: List<FileItem>): List<FileItem> = entries.filter { it.path in paths }
}
