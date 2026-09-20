package com.ryo.androidfilemanager.smb

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ryo.androidfilemanager.core.domain.FileSortOption
import com.ryo.androidfilemanager.ui.components.BrowseIconButton
import com.ryo.androidfilemanager.ui.components.DropdownChoiceButton
import com.ryo.androidfilemanager.ui.components.FileDisplayModeToggle

@Composable
internal fun SmbBrowserToolbar(
    gridMode: Boolean,
    onGridModeChange: (Boolean) -> Unit,
    selectedSort: FileSortOption,
    onSortSelected: (FileSortOption) -> Unit,
    selectedCount: Int,
    isDownloading: Boolean,
    isUploading: Boolean,
    onReload: () -> Unit,
    onUpload: () -> Unit,
    onDownloadSelected: () -> Unit,
    onClearSelection: () -> Unit,
    onEditConnection: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selectedCount > 0) {
        SelectedFilesToolbar(
            selectedCount = selectedCount,
            isDownloading = isDownloading,
            onDownloadSelected = onDownloadSelected,
            onClearSelection = onClearSelection,
        )
        return
    }

    Row(
        // 横向きだとヘッダーと1行にまとめるため、項目が増えても折り返さず横スクロールで収める
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileDisplayModeToggle(
            gridMode = gridMode,
            onGridModeChange = onGridModeChange,
        )
        BrowseIconButton(
            icon = Icons.Outlined.Refresh,
            contentDescription = "Reload SMB folder",
            onClick = onReload,
        )
        if (isUploading) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
            )
        } else {
            BrowseIconButton(
                icon = Icons.Outlined.FileUpload,
                contentDescription = "Upload files to this SMB folder",
                onClick = onUpload,
                emphasized = true,
            )
        }
        DropdownChoiceButton(
            options = FileSortOption.entries,
            selected = selectedSort,
            optionLabel = { it.label },
            icon = Icons.AutoMirrored.Outlined.Sort,
            contentDescription = "Sort by ${selectedSort.label}",
            onSelected = onSortSelected,
        )
        var moreMenuExpanded by rememberSaveable { mutableStateOf(false) }
        // DropdownMenu は直前の親レイアウトを基準に位置決めするので、ボタンと同じ Box に入れて
        // ボタンの直下に出す（Row の子として並べると Row の左端に出てしまう）
        Box {
            BrowseIconButton(
                icon = Icons.Outlined.MoreVert,
                contentDescription = "More actions",
                onClick = { moreMenuExpanded = true },
            )
            DropdownMenu(
                expanded = moreMenuExpanded,
                onDismissRequest = { moreMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Edit connection") },
                    leadingIcon = { Icon(imageVector = Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        moreMenuExpanded = false
                        onEditConnection()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Disconnect") },
                    leadingIcon = { Icon(imageVector = Icons.Outlined.LinkOff, contentDescription = null) },
                    onClick = {
                        moreMenuExpanded = false
                        onDisconnect()
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectedFilesToolbar(
    selectedCount: Int,
    isDownloading: Boolean,
    onDownloadSelected: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.weight(1f))
            FilledTonalButton(
                onClick = onDownloadSelected,
                enabled = !isDownloading,
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(text = "Download")
            }
            BrowseIconButton(
                icon = Icons.Outlined.Close,
                contentDescription = "Clear selection",
                onClick = onClearSelection,
            )
        }
    }
}
