package com.ryo.androidfilemanager.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ryo.androidfilemanager.data.model.FileItem
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
    val thumbnailResult by produceState<ThumbnailResult>(
        initialValue = initialIcon,
        key1 = file.path,
        key2 = file.size,
        key3 = file.modifiedAt,
    ) {
        value = thumbnailRepository.getThumbnail(file)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when (val result = thumbnailResult) {
            is ThumbnailResult.CachedFile -> AsyncImage(
                model = result.uri,
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            is ThumbnailResult.Icon -> ThumbnailIcon(label = result.descriptor.label)
            is ThumbnailResult.Unavailable -> ThumbnailIcon(label = IconResolver.resolve(file).label)
        }
    }
}

@Composable
private fun ThumbnailIcon(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.Bold,
    )
}

