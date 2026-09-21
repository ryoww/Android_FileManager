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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch

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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        modifier = Modifier.fillMaxSize(),
                    )

                    RootSection.SMB -> SmbConnectionScreen(
                        onOpenFile = { openedFile = it },
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        modifier = Modifier.fillMaxSize(),
                    )

                    RootSection.SETTINGS -> SettingsScreen(
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    fun selectRootSection(section: RootSection) {
        closeOpenedFile()
        rootSection = section
        scope.launch { drawerState.close() }
    }

    // 向きに依らず引き出しの中身と content は同じコンポジション位置に置く。
    // 向きごとに別のツリーへ分けると、回転でビューワーのサブツリーが破棄・再生成される
    // （動画がリスタートし、SMB ストリームは接続が閉じる）
    ModalNavigationDrawer(
        drawerState = drawerState,
        // ファイルを開いている間は端からのスワイプで引き出しを出さない（PDF の横スクロールや
        // 動画のシーク操作と競合するため）
        gesturesEnabled = openedFile == null,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "Android File Manager",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    label = { Text("Explorer") },
                    selected = rootSection == RootSection.EXPLORER && openedFile == null,
                    onClick = { selectRootSection(RootSection.EXPLORER) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
                    label = { Text("SMB") },
                    selected = rootSection == RootSection.SMB && openedFile == null,
                    onClick = { selectRootSection(RootSection.SMB) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = rootSection == RootSection.SETTINGS && openedFile == null,
                    onClick = { selectRootSection(RootSection.SETTINGS) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        // 安全領域のパディングはドロワー全体ではなく中身に付ける。ドロワー全体に付けると、
        // 横向きでカットアウト側の余白ぶんだけ閉じたシートの右端が画面左に覗いてしまう
        // （シートは容器の左端から自身の幅だけ左に隠れる計算で、余白は考慮されない）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (viewerFullScreen) Modifier else Modifier.safeDrawingPadding()),
        ) {
            content(Modifier)
        }
    }

    // 引き出しが開いているときの戻る操作は引き出しを閉じる。使っている Material3 の
    // ModalNavigationDrawer はこれを内包しておらず、そのままだと戻るでアプリが終了した。
    // 画面側の BackHandler より後に構成して優先させる（後から登録した方が先に呼ばれる）
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
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
