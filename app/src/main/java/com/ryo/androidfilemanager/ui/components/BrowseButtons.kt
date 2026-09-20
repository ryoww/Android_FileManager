package com.ryo.androidfilemanager.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
internal fun FileDisplayModeToggle(
    gridMode: Boolean,
    onGridModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // アイコンのみのセグメント切り替え。ラベルは付けず標準のリップルに任せる
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = gridMode,
            onClick = { onGridModeChange(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {},
            label = {
                Icon(
                    imageVector = Icons.Outlined.GridView,
                    contentDescription = "Grid view",
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        SegmentedButton(
            selected = !gridMode,
            onClick = { onGridModeChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {},
            label = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ViewList,
                    contentDescription = "List view",
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}

@Composable
internal fun BrowseIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    // emphasized はトーン付き、それ以外は標準の IconButton に委ねる
    if (emphasized) {
        FilledTonalIconButton(onClick = onClick, modifier = modifier) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    } else {
        IconButton(onClick = onClick, modifier = modifier) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}
