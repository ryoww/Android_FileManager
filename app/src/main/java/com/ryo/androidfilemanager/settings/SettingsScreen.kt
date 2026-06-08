package com.ryo.androidfilemanager.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ryo.androidfilemanager.data.cache.FileCacheRepository
import com.ryo.androidfilemanager.data.cache.formatCacheSize
import com.ryo.androidfilemanager.data.local.FileManagerAccess
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
    val currentContext = LocalContext.current
    val context = currentContext.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val cacheRepository = remember(context) {
        FileCacheRepository(context)
    }
    val scope = rememberCoroutineScope()
    var cacheSizes by remember { mutableStateOf(CacheSizeState()) }
    var hasFullStorageAccess by remember { mutableStateOf(FileManagerAccess.hasAllFilesAccess()) }
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasFullStorageAccess = FileManagerAccess.hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )

        SectionLabel(text = "FILE MANAGER ACCESS")
        SettingsCard {
            SettingsActionRow(
                title = "Full Storage Access",
                value = if (hasFullStorageAccess) {
                    "Enabled. Explorer can browse Download and internal storage directly."
                } else {
                    "Disabled. SAF cannot grant access to Download itself on Android 11+."
                },
                actionLabel = if (hasFullStorageAccess) "Open Settings" else "Enable",
                onClick = {
                    runCatching {
                        currentContext.startActivity(FileManagerAccess.settingsIntent(context))
                    }.onFailure {
                        currentContext.startActivity(FileManagerAccess.appSettingsIntent(context))
                    }
                },
            )
        }

        SectionLabel(text = "CACHE MANAGEMENT")
        SettingsCard {
            SettingsActionRow(
                title = "Total Cache",
                value = formatCacheSize(cacheSizes.total),
                actionLabel = "Clear All",
                onClick = {
                    scope.launch {
                        cacheRepository.clearAll()
                        statusMessage = "All cache directories were cleared."
                        refreshCacheSizes()
                    }
                },
            )
            SettingsActionRow(
                title = "SMB Temporary Files",
                value = formatCacheSize(cacheSizes.smb),
                actionLabel = "Clear SMB Cache",
                onClick = {
                    scope.launch {
                        cacheRepository.clearSmbCache()
                        statusMessage = "SMB temporary files were cleared."
                        refreshCacheSizes()
                    }
                },
            )
            SettingsActionRow(
                title = "Thumbnail Cache",
                value = formatCacheSize(cacheSizes.thumbnails),
                actionLabel = "Clear Thumbnail Cache",
                onClick = {
                    scope.launch {
                        cacheRepository.clearThumbnailCache()
                        statusMessage = "Thumbnail cache was cleared."
                        refreshCacheSizes()
                    }
                },
            )
        }

        Text(
            text = "Clearing cache removes temporary SMB files and generated thumbnails.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionLabel(text = "ABOUT")
        SettingsCard {
            Text(
                text = "Version",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Android File Manager 0.1.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    value: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onClick) {
            Text(text = actionLabel)
        }
    }
}
