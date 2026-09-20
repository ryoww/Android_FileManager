package com.ryo.androidfilemanager.core.application

import com.ryo.androidfilemanager.core.application.port.FileSource
import com.ryo.androidfilemanager.core.domain.DirectoryNavigation
import com.ryo.androidfilemanager.core.domain.FileItem

/** ナビゲーション操作の結果、遷移後の状態とその一覧をまとめて返す */
data class BrowseOutcome(
    val navigation: DirectoryNavigation,
    val entries: List<FileItem>,
)

/** DirectoryNavigation の遷移と FileSource からの一覧取得をまとめるユースケース */
class DirectoryBrowser(private val fileSource: FileSource) {
    suspend fun enter(navigation: DirectoryNavigation, path: String): BrowseOutcome {
        val next = navigation.enter(path)
        return BrowseOutcome(next, fileSource.list(path))
    }

    // 戻れない場合は FileSource を呼ばず null を返す（root で誤って一覧取得しないため）
    suspend fun up(navigation: DirectoryNavigation): BrowseOutcome? {
        if (!navigation.canNavigateUp) return null
        val next = navigation.up()
        val path = requireNotNull(next.currentPath)
        return BrowseOutcome(next, fileSource.list(path))
    }

    suspend fun reload(navigation: DirectoryNavigation): BrowseOutcome {
        val path = navigation.currentPath ?: error("No current path to reload.")
        return BrowseOutcome(navigation, fileSource.list(path))
    }
}
