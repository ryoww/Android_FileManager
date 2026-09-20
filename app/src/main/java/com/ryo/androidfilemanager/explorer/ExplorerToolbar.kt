package com.ryo.androidfilemanager.explorer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ryo.androidfilemanager.ui.components.BrowseIconButton
import com.ryo.androidfilemanager.ui.components.DropdownChoiceButton
import com.ryo.androidfilemanager.ui.components.FileDisplayModeToggle

@Composable
internal fun ExplorerToolbar(
    gridMode: Boolean,
    onGridModeChange: (Boolean) -> Unit,
    selectedFilter: FileFilter,
    onFilterSelected: (FileFilter) -> Unit,
    selectedSort: FileSortOption,
    onSortSelected: (FileSortOption) -> Unit,
    onReload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileDisplayModeToggle(
            gridMode = gridMode,
            onGridModeChange = onGridModeChange,
        )
        BrowseIconButton(
            icon = Icons.Outlined.Refresh,
            contentDescription = "Reload files",
            onClick = onReload,
        )
        DropdownChoiceButton(
            options = FileFilter.entries,
            selected = selectedFilter,
            optionLabel = { it.label },
            icon = Icons.Outlined.FilterList,
            contentDescription = "Filter: ${selectedFilter.label}",
            onSelected = onFilterSelected,
        )
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
