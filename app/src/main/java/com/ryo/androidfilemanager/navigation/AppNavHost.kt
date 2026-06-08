package com.ryo.androidfilemanager.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import com.ryo.androidfilemanager.data.model.OpenedFile
import com.ryo.androidfilemanager.explorer.ExplorerScreen
import com.ryo.androidfilemanager.settings.SettingsScreen
import com.ryo.androidfilemanager.smb.SmbConnectionScreen
import com.ryo.androidfilemanager.viewer.ViewerRouter

private enum class RootSection {
    EXPLORER,
    SMB,
    SETTINGS,
}

@Composable
fun AndroidFileManagerApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF3A86FF),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF123A72),
            onPrimaryContainer = Color(0xFFE8F1FF),
            secondary = Color(0xFF74D0FF),
            background = Color(0xFF06111D),
            surface = Color(0xFF0B1724),
            surfaceContainerHighest = Color(0xCC122235),
            onSurface = Color(0xFFF4F8FF),
            onSurfaceVariant = Color(0xFF9FAEC2),
            outline = Color(0xFF24364D),
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF081827),
                            Color(0xFF04101B),
                            Color(0xFF020812),
                        ),
                    ),
                )
                .safeDrawingPadding(),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            AppNavHost()
        }
    }
}

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
) {
    var openedFile by remember { mutableStateOf<OpenedFile?>(null) }
    var rootSection by remember { mutableStateOf(RootSection.EXPLORER) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            AppBottomNavigation(
                rootSection = rootSection,
                viewerSelected = openedFile != null,
                onRootSelected = { section ->
                    openedFile = null
                    rootSection = section
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            openedFile?.let { file ->
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(onClick = { openedFile = null }) {
                            Text(text = "Back")
                        }
                        Text(
                            text = file.viewerType.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    ViewerRouter(
                        openedFile = file,
                        modifier = Modifier.weight(1f),
                    )
                }
            } ?: when (rootSection) {
                RootSection.EXPLORER -> ExplorerScreen(
                    onOpenFile = { openedFile = it },
                    modifier = Modifier.fillMaxSize(),
                )

                RootSection.SMB -> SmbConnectionScreen(
                    onOpenFile = { openedFile = it },
                    modifier = Modifier.fillMaxSize(),
                )

                RootSection.SETTINGS -> SettingsScreen(
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun AppBottomNavigation(
    rootSection: RootSection,
    viewerSelected: Boolean,
    onRootSelected: (RootSection) -> Unit,
) {
    NavigationBar(
        containerColor = Color(0xEE081827),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        NavigationBarItem(
            selected = rootSection == RootSection.EXPLORER && !viewerSelected,
            onClick = { onRootSelected(RootSection.EXPLORER) },
            icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
            label = { Text(text = "Explorer") },
        )
        NavigationBarItem(
            selected = rootSection == RootSection.SMB && !viewerSelected,
            onClick = { onRootSelected(RootSection.SMB) },
            icon = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
            label = { Text(text = "SMB") },
        )
        NavigationBarItem(
            selected = viewerSelected,
            onClick = {},
            enabled = viewerSelected,
            icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
            label = { Text(text = "Viewer") },
        )
        NavigationBarItem(
            selected = rootSection == RootSection.SETTINGS && !viewerSelected,
            onClick = { onRootSelected(RootSection.SETTINGS) },
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            label = { Text(text = "Settings") },
        )
    }
}
