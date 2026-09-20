package com.ryo.androidfilemanager.core.domain

/** パススタックでディレクトリ階層を表す。UI の戻る操作や再読み込みはこれを介して行う。 */
data class DirectoryNavigation(val pathStack: List<String>) {
    val currentPath: String?
        get() = pathStack.lastOrNull()

    // 1 件だけなら root なので上へは戻れない
    val canNavigateUp: Boolean
        get() = pathStack.size > 1

    fun enter(path: String): DirectoryNavigation = DirectoryNavigation(pathStack + path)

    // 戻れない場合は新規インスタンスを作らず自身を返す（呼び出し側が変化なしを判定しやすいように）
    fun up(): DirectoryNavigation =
        if (canNavigateUp) DirectoryNavigation(pathStack.dropLast(1)) else this

    companion object {
        val Empty = DirectoryNavigation(emptyList())

        fun root(path: String): DirectoryNavigation = DirectoryNavigation(listOf(path))
    }
}
