package com.ryo.androidfilemanager.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ryo.androidfilemanager.data.cache.FileCacheRepository
import com.ryo.androidfilemanager.data.cache.formatCacheSize
import kotlinx.coroutines.launch

private data class CacheSizeState(
    val total: Long = 0L,
    val smb: Long = 0L,
    val thumbnails: Long = 0L,
)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val cacheRepository = remember(context) {
        FileCacheRepository(context)
    }
    val scope = rememberCoroutineScope()
    var cacheSizes by remember { mutableStateOf(CacheSizeState()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshCacheSizes() {
        scope.launch {
            cacheSizes = CacheSizeState(
                total = cacheRepository.getTotalCacheSize(),
                smb = cacheRepository.getSmbCacheSize(),
                thumbnails = cacheRepository.getThumbnailCacheSize(),
            )
        }
    }

    LaunchedEffect(Unit) {
        cacheSizes = CacheSizeState(
            total = cacheRepository.getTotalCacheSize(),
            smb = cacheRepository.getSmbCacheSize(),
            thumbnails = cacheRepository.getThumbnailCacheSize(),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Cache usage",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(text = "Total cache: ${formatCacheSize(cacheSizes.total)}")
                Text(text = "SMB temporary files: ${formatCacheSize(cacheSizes.smb)}")
                Text(text = "Thumbnails: ${formatCacheSize(cacheSizes.thumbnails)}")
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = {
                    scope.launch {
                        cacheRepository.clearAll()
                        statusMessage = "All cache directories were cleared."
                        refreshCacheSizes()
                    }
                },
            ) {
                Text(text = "Clear all")
            }
            Button(
                onClick = {
                    scope.launch {
                        cacheRepository.clearSmbCache()
                        statusMessage = "SMB temporary files were cleared."
                        refreshCacheSizes()
                    }
                },
            ) {
                Text(text = "Clear SMB")
            }
            Button(
                onClick = {
                    scope.launch {
                        cacheRepository.clearThumbnailCache()
                        statusMessage = "Thumbnail cache was cleared."
                        refreshCacheSizes()
                    }
                },
            ) {
                Text(text = "Clear thumbnails")
            }
        }

        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "App info: Android File Manager 0.1.0",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

