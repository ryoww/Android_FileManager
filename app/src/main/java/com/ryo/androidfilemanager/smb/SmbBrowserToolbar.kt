package com.ryo.androidfilemanager.smb

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ryo.androidfilemanager.explorer.FileSortOption
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
        modifier = Modifier.fillMaxWidth(),
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
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
