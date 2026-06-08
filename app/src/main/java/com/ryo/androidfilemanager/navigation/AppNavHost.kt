package com.ryo.androidfilemanager.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Settings
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
            primary = Color(0xFF3F8CFF),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF173D72),
            onPrimaryContainer = Color(0xFFE8F1FF),
            secondary = Color(0xFF76D8FF),
            background = Color(0xFF030B13),
            surface = Color(0xFF071522),
            surfaceContainerHighest = Color(0xB3122437),
            onSurface = Color(0xFFF4F8FF),
            onSurfaceVariant = Color(0xFFAAB8C8),
            outline = Color(0xFF263B52),
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF061725),
                            Color(0xFF03101A),
                            Color(0xFF020711),
                        ),
                    ),
                ),
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
    var viewerFullScreen by remember { mutableStateOf(false) }

    SystemBarsHiddenEffect(hidden = viewerFullScreen)
    BackHandler(enabled = openedFile != null) {
        if (viewerFullScreen) {
            viewerFullScreen = false
        } else {
            openedFile = null
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(if (viewerFullScreen) Modifier else Modifier.safeDrawingPadding()),
        containerColor = Color.Transparent,
        bottomBar = {
            if (!viewerFullScreen) {
                AppBottomNavigation(
                    rootSection = rootSection,
                    viewerSelected = openedFile != null,
                    onRootSelected = { section ->
                        viewerFullScreen = false
                        openedFile = null
                        rootSection = section
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (viewerFullScreen) Modifier else Modifier.padding(innerPadding)),
        ) {
            openedFile?.let { file ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (viewerFullScreen) Modifier.background(Color.Black) else Modifier),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!viewerFullScreen) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(onClick = { openedFile = null }) {
                                    Text(text = "Back")
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = file.viewerType.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                FilledTonalIconButton(
                                    onClick = { viewerFullScreen = true },
                                    modifier = Modifier.size(44.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = Color(0x331C4E89),
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Icon(
                                        Icons.Outlined.Fullscreen,
                                        contentDescription = "Full screen",
                                    )
                                }
                            }
                        }
                        ViewerRouter(
                            openedFile = file,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (viewerFullScreen) {
                        FilledTonalIconButton(
                            onClick = { viewerFullScreen = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(14.dp)
                                .size(44.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color(0xAA0B1724),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Icon(
                                Icons.Outlined.FullscreenExit,
                                contentDescription = "Exit full screen",
                            )
                        }
                    }
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
private fun SystemBarsHiddenEffect(hidden: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    if (view.isInEditMode) {
        return
    }

    DisposableEffect(hidden, context, view) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (hidden) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            if (hidden) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun AppBottomNavigation(
    rootSection: RootSection,
    viewerSelected: Boolean,
    onRootSelected: (RootSection) -> Unit,
) {
    NavigationBar(
        containerColor = Color(0xF2051421),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        NavigationBarItem(
            selected = rootSection == RootSection.EXPLORER && !viewerSelected,
            onClick = { onRootSelected(RootSection.EXPLORER) },
            icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
            label = { Text(text = "Explorer") },
            colors = appNavItemColors(),
        )
        NavigationBarItem(
            selected = rootSection == RootSection.SMB && !viewerSelected,
            onClick = { onRootSelected(RootSection.SMB) },
            icon = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
            label = { Text(text = "SMB") },
            colors = appNavItemColors(),
        )
        NavigationBarItem(
            selected = viewerSelected,
            onClick = {},
            enabled = viewerSelected,
            icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
            label = { Text(text = "Viewer") },
            colors = appNavItemColors(),
        )
        NavigationBarItem(
            selected = rootSection == RootSection.SETTINGS && !viewerSelected,
            onClick = { onRootSelected(RootSection.SETTINGS) },
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            label = { Text(text = "Settings") },
            colors = appNavItemColors(),
        )
    }
}

@Composable
private fun appNavItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.secondary,
    indicatorColor = Color(0x334B95FF),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
