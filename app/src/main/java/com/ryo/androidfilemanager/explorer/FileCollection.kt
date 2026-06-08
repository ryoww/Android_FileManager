package com.ryo.androidfilemanager.explorer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ryo.androidfilemanager.data.model.FileItem
import com.ryo.androidfilemanager.data.thumbnail.ThumbnailRepository

@Composable
internal fun FileCollection(
    gridMode: Boolean,
    files: List<FileItem>,
    thumbnailRepository: ThumbnailRepository,
    onFileClick: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
    selectedPaths: Set<String> = emptySet(),
    onFileLongClick: ((FileItem) -> Unit)? = null,
) {
    if (gridMode) {
        val gridState = rememberLazyGridState()
        val scrollbarMetrics by remember {
            derivedStateOf { gridState.scrollbarMetrics() }
        }

        Box(modifier = modifier.fillMaxSize()) {
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
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    } else {
        val listState = rememberLazyListState()
        val scrollbarMetrics by remember {
            derivedStateOf { listState.scrollbarMetrics() }
        }

        Box(modifier = modifier.fillMaxSize()) {
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
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun FileScrollIndicator(
    metrics: ScrollbarMetrics?,
    modifier: Modifier = Modifier,
) {
    val currentMetrics = metrics ?: return
    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(8.dp)
            .padding(top = 6.dp, bottom = 6.dp, end = 2.dp),
    ) {
        val trackWidth = 2.dp.toPx()
        val thumbWidth = 3.dp.toPx()
        val thumbHeight = (size.height * currentMetrics.thumbHeightFraction)
            .coerceAtLeast(28.dp.toPx())
            .coerceAtMost(size.height)
        val thumbTop = (size.height - thumbHeight) * currentMetrics.positionFraction
        val trackLeft = (size.width - trackWidth) / 2f
        val thumbLeft = (size.width - thumbWidth) / 2f

        drawRoundRect(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = Offset(trackLeft, 0f),
            size = Size(trackWidth, size.height),
            cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f),
        )
        drawRoundRect(
            color = Color(0xFF74D0FF).copy(alpha = 0.78f),
            topLeft = Offset(thumbLeft, thumbTop),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f),
        )
    }
}

private data class ScrollbarMetrics(
    val positionFraction: Float,
    val thumbHeightFraction: Float,
)

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

private fun scrollbarMetrics(
    firstVisibleIndex: Int,
    visibleItemCount: Int,
    totalItemCount: Int,
): ScrollbarMetrics {
    val maxFirstVisibleIndex = (totalItemCount - visibleItemCount).coerceAtLeast(1)
    val thumbHeightFraction = (visibleItemCount.toFloat() / totalItemCount.toFloat())
        .coerceIn(0.08f, 1f)
    val positionFraction = (firstVisibleIndex.toFloat() / maxFirstVisibleIndex.toFloat())
        .coerceIn(0f, 1f)

    return ScrollbarMetrics(
        positionFraction = positionFraction,
        thumbHeightFraction = thumbHeightFraction,
    )
}
