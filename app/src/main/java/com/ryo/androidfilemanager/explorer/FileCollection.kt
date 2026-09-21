package com.ryo.androidfilemanager.explorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.data.thumbnail.ThumbnailRepository
import com.ryo.androidfilemanager.ui.components.ScrollbarMetrics
import com.ryo.androidfilemanager.ui.components.VerticalScrollbar
import com.ryo.androidfilemanager.ui.components.scrollbarMetrics
import com.ryo.androidfilemanager.ui.components.scrollbarTargetIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun FileCollection(
    gridMode: Boolean,
    files: List<FileItem>,
    thumbnailRepository: ThumbnailRepository,
    onFileClick: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
    selectedPaths: Set<String> = emptySet(),
    onFileLongClick: ((FileItem) -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    if (gridMode) {
        // スクロール状態はフォルダごとに保存・復元する。呼び出し側が
        // SaveableStateProvider(currentPath) で包むので、フォルダに入ると新規、
        // 戻ると復元になる。ここでパスをキーにすると復元ができないため、キーは付けない（Why not）
        val gridState = rememberSaveable(saver = LazyGridState.Saver) {
            LazyGridState()
        }
        val scrollScope = rememberCoroutineScope()
        val scrollbarMetrics by remember(gridState) {
            derivedStateOf { gridState.scrollbarMetrics() }
        }
        val visibleItemCount by remember(gridState) {
            derivedStateOf { gridState.layoutInfo.visibleItemsInfo.size }
        }
        LaunchedEffect(files, gridState, thumbnailRepository) {
            thumbnailRepository.updateVisibleThumbnails(emptyList())
            snapshotFlow { gridState.visibleFilesWithBuffer(files) }
                .distinctUntilChanged { previous, current ->
                    previous.sameThumbnailTargetsAs(current)
                }
                .collectLatest { visibleFiles ->
                    delay(VIEWPORT_STABILIZATION_MS)
                    thumbnailRepository.updateVisibleThumbnails(visibleFiles)
                }
        }

        RefreshableContainer(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier.fillMaxSize(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = files,
                    key = { it.path },
                ) { file ->
                    FileGridItem(
                        file = file,
                        thumbnailRepository = thumbnailRepository,
                        selected = selectedPaths.contains(file.path),
                        onClick = { onFileClick(file) },
                        onLongClick = onFileLongClick?.let { longClick ->
                            { longClick(file) }
                        },
                    )
                }
            }
            FileScrollIndicator(
                metrics = scrollbarMetrics,
                totalItemCount = files.size,
                visibleItemCount = visibleItemCount,
                scrollInProgress = gridState.isScrollInProgress,
                onScrollToIndex = { index ->
                    scrollScope.launch {
                        gridState.scrollToItem(index)
                    }
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    } else {
        val listState = rememberSaveable(saver = LazyListState.Saver) {
            LazyListState()
        }
        val scrollScope = rememberCoroutineScope()
        val scrollbarMetrics by remember(listState) {
            derivedStateOf { listState.scrollbarMetrics() }
        }
        val visibleItemCount by remember(listState) {
            derivedStateOf { listState.layoutInfo.visibleItemsInfo.size }
        }
        LaunchedEffect(files, listState, thumbnailRepository) {
            thumbnailRepository.updateVisibleThumbnails(emptyList())
            snapshotFlow { listState.visibleFilesWithBuffer(files) }
                .distinctUntilChanged { previous, current ->
                    previous.sameThumbnailTargetsAs(current)
                }
                .collectLatest { visibleFiles ->
                    delay(VIEWPORT_STABILIZATION_MS)
                    thumbnailRepository.updateVisibleThumbnails(visibleFiles)
                }
        }

        RefreshableContainer(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = files,
                    key = { it.path },
                ) { file ->
                    FileListItem(
                        file = file,
                        thumbnailRepository = thumbnailRepository,
                        selected = selectedPaths.contains(file.path),
                        onClick = { onFileClick(file) },
                        onLongClick = onFileLongClick?.let { longClick ->
                            { longClick(file) }
                        },
                    )
                }
            }
            FileScrollIndicator(
                metrics = scrollbarMetrics,
                totalItemCount = files.size,
                visibleItemCount = visibleItemCount,
                scrollInProgress = listState.isScrollInProgress,
                onScrollToIndex = { index ->
                    scrollScope.launch {
                        listState.scrollToItem(index)
                    }
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/**
 * onRefresh が null なら素の Box、そうでなければ Pull-to-Refresh 対応の Box として振る舞う。
 * isRefreshing はファイルオープン中も true になるため、ユーザーが実際に pull した場合のみ
 * インジケータを表示する（pullRequested）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableContainer(
    isRefreshing: Boolean,
    onRefresh: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (onRefresh == null) {
        Box(modifier = modifier, content = content)
        return
    }

    var pullRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) pullRequested = false
    }
    PullToRefreshBox(
        isRefreshing = pullRequested && isRefreshing,
        onRefresh = {
            pullRequested = true
            onRefresh()
        },
        modifier = modifier,
        content = content,
    )
}

/**
 * [VerticalScrollbar] の薄いラッパー。エクスプローラー一覧は「何番目のアイテムまで
 * スクロールするか」で扱うため、共通コンポーネントが返す 0f..1f の割合を index に変換する。
 * Android 標準のファストスクローラーと同じく、スクロール中とドラッグ中だけ表示する。
 */
@Composable
private fun FileScrollIndicator(
    metrics: ScrollbarMetrics?,
    totalItemCount: Int,
    visibleItemCount: Int,
    scrollInProgress: Boolean,
    onScrollToIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    VerticalScrollbar(
        metrics = metrics,
        onDragFraction = { fraction ->
            onScrollToIndex(
                scrollbarTargetIndex(
                    positionFraction = fraction,
                    totalItemCount = totalItemCount,
                    visibleItemCount = visibleItemCount,
                ),
            )
        },
        modifier = modifier,
        autoHide = true,
        scrollInProgress = scrollInProgress,
    )
}

private fun LazyListState.scrollbarMetrics(): ScrollbarMetrics? {
    val visibleItems = layoutInfo.visibleItemsInfo
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems == 0 || visibleItems.isEmpty() || visibleItems.size >= totalItems) {
        return null
    }

    return scrollbarMetrics(
        firstVisibleIndex = visibleItems.first().index,
        visibleItemCount = visibleItems.size,
        totalItemCount = totalItems,
    )
}

private fun LazyListState.visibleFilesWithBuffer(files: List<FileItem>): List<FileItem> {
    val visibleItems = layoutInfo.visibleItemsInfo
    if (files.isEmpty() || visibleItems.isEmpty()) {
        return emptyList()
    }

    val firstIndex = visibleItems.first().index
    val lastIndex = visibleItems.last().index
    return itemsInThumbnailPriorityOrder(files, firstIndex, lastIndex)
}

private fun LazyGridState.scrollbarMetrics(): ScrollbarMetrics? {
    val visibleItems = layoutInfo.visibleItemsInfo
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems == 0 || visibleItems.isEmpty() || visibleItems.size >= totalItems) {
        return null
    }

    return scrollbarMetrics(
        firstVisibleIndex = visibleItems.minOf { it.index },
        visibleItemCount = visibleItems.size,
        totalItemCount = totalItems,
    )
}

private fun LazyGridState.visibleFilesWithBuffer(files: List<FileItem>): List<FileItem> {
    val visibleItems = layoutInfo.visibleItemsInfo
    if (files.isEmpty() || visibleItems.isEmpty()) {
        return emptyList()
    }

    val firstIndex = visibleItems.minOf { it.index }
    val lastIndex = visibleItems.maxOf { it.index }
    return itemsInThumbnailPriorityOrder(files, firstIndex, lastIndex)
}

internal fun <T> itemsInThumbnailPriorityOrder(
    items: List<T>,
    firstVisibleIndex: Int,
    lastVisibleIndex: Int,
    bufferSize: Int = VISIBLE_THUMBNAIL_BUFFER_ITEMS,
): List<T> {
    if (items.isEmpty() || firstVisibleIndex > lastVisibleIndex) {
        return emptyList()
    }

    val first = firstVisibleIndex.coerceIn(items.indices)
    val last = lastVisibleIndex.coerceIn(first, items.lastIndex)
    val forwardEnd = (last + bufferSize).coerceAtMost(items.lastIndex)
    val backwardStart = (first - bufferSize).coerceAtLeast(0)

    return buildList {
        addAll(items.subList(first, last + 1))
        if (last < forwardEnd) {
            addAll(items.subList(last + 1, forwardEnd + 1))
        }
        if (backwardStart < first) {
            addAll(items.subList(backwardStart, first))
        }
    }
}

private fun List<FileItem>.sameThumbnailTargetsAs(other: List<FileItem>): Boolean {
    if (size != other.size) {
        return false
    }
    return indices.all { index ->
        val left = this[index]
        val right = other[index]
        left.path == right.path &&
            left.size == right.size &&
            left.modifiedAt == right.modifiedAt
    }
}

private const val VISIBLE_THUMBNAIL_BUFFER_ITEMS = 0
private const val VIEWPORT_STABILIZATION_MS = 120L
