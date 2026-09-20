package com.ryo.androidfilemanager.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.ryo.androidfilemanager.core.domain.FileItem
import com.ryo.androidfilemanager.data.thumbnail.IconDescriptor
import com.ryo.androidfilemanager.data.thumbnail.IconResolver
import com.ryo.androidfilemanager.data.thumbnail.ThumbnailRepository
import com.ryo.androidfilemanager.data.thumbnail.ThumbnailResult

@Composable
fun FileThumbnail(
    file: FileItem,
    thumbnailRepository: ThumbnailRepository,
    modifier: Modifier = Modifier,
) {
    val initialIcon = ThumbnailResult.Icon(IconResolver.resolve(file))
    val contentAspectRatio = thumbnailContentAspectRatio(file)

    LaunchedEffect(file.path, file.size, file.modifiedAt, thumbnailRepository) {
        thumbnailRepository.requestThumbnail(file)
    }

    // version はキーにせず内部で購読する。キーにすると更新通知のたびに
    // produceState が再起動して表示がアイコンへ戻り、全アイテムの再構築が
    // 走ってスクロール位置の暴走やちらつきの原因になる
    val thumbnailResult by produceState<ThumbnailResult>(
        initialIcon,
        file.path,
        file.size,
        file.modifiedAt,
        thumbnailRepository,
    ) {
        thumbnailRepository.observeThumbnailVersion().collect {
            val result = thumbnailRepository.getThumbnail(file)
            if (result != value) {
                value = result
            }
        }
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        when (val result = thumbnailResult) {
            is ThumbnailResult.CachedFile -> AsyncImage(
                model = result.uri,
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (contentAspectRatio != null) {
                    ContentScale.Fit
                } else {
                    ContentScale.Crop
                },
            )

            is ThumbnailResult.Icon -> ThumbnailIcon(descriptor = result.descriptor)
            is ThumbnailResult.Unavailable -> ThumbnailIcon(descriptor = IconResolver.resolve(file))
        }
    }
}

@Composable
private fun ThumbnailIcon(descriptor: IconDescriptor) {
    Icon(
        imageVector = descriptor.icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxSize(0.45f),
    )
}
