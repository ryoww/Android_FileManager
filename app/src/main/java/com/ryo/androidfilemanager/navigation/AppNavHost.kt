package com.ryo.androidfilemanager.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ryo.androidfilemanager.core.domain.OpenedFile
import com.ryo.androidfilemanager.core.domain.ViewerType
import com.ryo.androidfilemanager.explorer.ExplorerScreen
import com.ryo.androidfilemanager.settings.SettingsScreen
import com.ryo.androidfilemanager.smb.SmbConnectionScreen
import com.ryo.androidfilemanager.ui.theme.AppTypography
import com.ryo.androidfilemanager.viewer.ViewerFullScreenExitButton
import com.ryo.androidfilemanager.viewer.ViewerRouter
import com.ryo.androidfilemanager.viewer.ViewerTopBar

private enum class RootSection {
    EXPLORER,
    SMB,
    SETTINGS,
}

@Composable
fun AndroidFileManagerApp() {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    // Android 12+ は端末の壁紙に合わせた Dynamic Color、それ未満は Material3 デフォルト配色を使う
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        isDark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
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
    var openedFile by rememberSaveable(stateSaver = OpenedFileSaver) { mutableStateOf<OpenedFile?>(null) }
    var rootSection by rememberSaveable { mutableStateOf(RootSection.EXPLORER) }
    var viewerFullScreen by rememberSaveable { mutableStateOf(false) }
    // ユーザーが横向きのまま全画面を手動で解除した直後は、自動再突入させないための抑止フラグ
    var suppressAutoFullScreen by rememberSaveable { mutableStateOf(false) }
    val saveableStateHolder = rememberSaveableStateHolder()

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isVideo = openedFile?.viewerType == ViewerType.Video

    SystemBarsHiddenEffect(hidden = viewerFullScreen)
    BackHandler(enabled = openedFile != null) {
        if (viewerFullScreen) {
            val decision = resolveVideoFullScreenOnUserExit(isVideo = isVideo, isLandscape = isLandscape)
            viewerFullScreen = decision.fullScreen
            suppressAutoFullScreen = decision.suppressAutoFullScreen
            if (isVideo) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        } else {
            openedFile = null
        }
    }

    // 動画ビューワーを開いている間だけ、端末の向きに合わせて全画面状態を自動で追従させる
    LaunchedEffect(isLandscape, openedFile) {
        val decision = resolveVideoFullScreenOnOrientationChange(
            isVideo = isVideo,
            isLandscape = isLandscape,
            currentFullScreen = viewerFullScreen,
            suppressAutoFullScreen = suppressAutoFullScreen,
        )
        viewerFullScreen = decision.fullScreen
        suppressAutoFullScreen = decision.suppressAutoFullScreen
    }

    // openedFile が閉じられたら全画面・抑止・向き固定をすべてリセットする
    DisposableEffect(openedFile) {
        onDispose {
            if (openedFile == null) {
                viewerFullScreen = false
                suppressAutoFullScreen = false
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    fun closeOpenedFile() {
        openedFile = null
        viewerFullScreen = false
        suppressAutoFullScreen = false
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    fun enterFullScreen() {
        viewerFullScreen = true
        if (isVideo) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    fun exitFullScreen() {
        val decision = resolveVideoFullScreenOnUserExit(isVideo = isVideo, isLandscape = isLandscape)
        viewerFullScreen = decision.fullScreen
        suppressAutoFullScreen = decision.suppressAutoFullScreen
        if (isVideo) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val content: @Composable (Modifier) -> Unit = { paddingModifier ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (viewerFullScreen) Modifier else paddingModifier),
        ) {
            openedFile?.let { file ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (viewerFullScreen) Modifier.background(Color.Black) else Modifier),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!viewerFullScreen) {
                            ViewerTopBar(
                                openedFile = file,
                                onBack = { closeOpenedFile() },
                                onEnterFullScreen = { enterFullScreen() },
                            )
                        }
                        ViewerRouter(
                            openedFile = file,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (viewerFullScreen) {
                        ViewerFullScreenExitButton(
                            onClick = { exitFullScreen() },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(14.dp),
                        )
                    }
                }
            } ?: saveableStateHolder.SaveableStateProvider(rootSection.name) {
                when (rootSection) {
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

    val onRootSelected: (RootSection) -> Unit = { section ->
        closeOpenedFile()
        rootSection = section
    }

    // 向きごとに Scaffold を分けると、回転でビューワーのサブツリーが別のコンポジション位置に
    // 移って破棄・再生成される（動画がリスタートし、SMB ストリームは接続が閉じる）。
    // 1 つの Scaffold の中で「レールを出すか / 下タブを出すか」だけを切り替える
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(if (viewerFullScreen) Modifier else Modifier.safeDrawingPadding()),
        bottomBar = {
            if (!isLandscape && !viewerFullScreen) {
                AppBottomNavigation(
                    rootSection = rootSection,
                    viewerSelected = openedFile != null,
                    onRootSelected = onRootSelected,
                )
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .then(if (viewerFullScreen) Modifier else Modifier.padding(innerPadding)),
        ) {
            if (isLandscape && !viewerFullScreen) {
                AppNavigationRail(
                    rootSection = rootSection,
                    viewerSelected = openedFile != null,
                    onRootSelected = onRootSelected,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                content(Modifier)
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

private data class AppNavItem(
    val section: RootSection?,
    val icon: ImageVector,
    val label: String,
)

private val appNavItems = listOf(
    AppNavItem(RootSection.EXPLORER, Icons.Outlined.Folder, "Explorer"),
    AppNavItem(RootSection.SMB, Icons.Outlined.Cloud, "SMB"),
    AppNavItem(null, Icons.Outlined.Description, "Viewer"),
    AppNavItem(RootSection.SETTINGS, Icons.Outlined.Settings, "Settings"),
)

@Composable
private fun AppBottomNavigation(
    rootSection: RootSection,
    viewerSelected: Boolean,
    onRootSelected: (RootSection) -> Unit,
) {
    NavigationBar {
        appNavItems.forEach { item ->
            val isViewerItem = item.section == null
            NavigationBarItem(
                selected = if (isViewerItem) viewerSelected else item.section == rootSection && !viewerSelected,
                onClick = { item.section?.let(onRootSelected) },
                enabled = if (isViewerItem) viewerSelected else true,
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(text = item.label) },
            )
        }
    }
}

@Composable
private fun AppNavigationRail(
    rootSection: RootSection,
    viewerSelected: Boolean,
    onRootSelected: (RootSection) -> Unit,
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
    ) {
        appNavItems.forEach { item ->
            val isViewerItem = item.section == null
            NavigationRailItem(
                selected = if (isViewerItem) viewerSelected else item.section == rootSection && !viewerSelected,
                onClick = { item.section?.let(onRootSelected) },
                enabled = if (isViewerItem) viewerSelected else true,
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(text = item.label) },
            )
        }
    }
}
