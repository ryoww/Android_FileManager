package com.ryo.androidfilemanager.navigation

/** [resolveVideoFullScreenOnOrientationChange] / [resolveVideoFullScreenOnUserExit] の結果。 */
internal data class VideoFullScreenDecision(val fullScreen: Boolean, val suppressAutoFullScreen: Boolean)

/**
 * 端末の向きが変わったときの全画面状態を決める。
 * - 動画でなければ何もしない（現状維持）
 * - 横向きになった: 「ユーザーが横向きのまま全画面を解除した」抑止フラグが立っていなければ全画面にする
 * - 縦向きになった: 全画面を解除し、抑止フラグも下ろす
 */
internal fun resolveVideoFullScreenOnOrientationChange(
    isVideo: Boolean,
    isLandscape: Boolean,
    currentFullScreen: Boolean,
    suppressAutoFullScreen: Boolean,
): VideoFullScreenDecision {
    if (!isVideo) {
        return VideoFullScreenDecision(fullScreen = currentFullScreen, suppressAutoFullScreen = suppressAutoFullScreen)
    }
    return if (isLandscape) {
        VideoFullScreenDecision(
            fullScreen = if (suppressAutoFullScreen) currentFullScreen else true,
            suppressAutoFullScreen = suppressAutoFullScreen,
        )
    } else {
        VideoFullScreenDecision(fullScreen = false, suppressAutoFullScreen = false)
    }
}

/** ユーザーが全画面解除ボタン/戻るで解除したとき。横向きなら抑止フラグを立てる（すぐ再突入しないため） */
internal fun resolveVideoFullScreenOnUserExit(isVideo: Boolean, isLandscape: Boolean): VideoFullScreenDecision {
    if (!isVideo) {
        return VideoFullScreenDecision(fullScreen = false, suppressAutoFullScreen = false)
    }
    return VideoFullScreenDecision(fullScreen = false, suppressAutoFullScreen = isLandscape)
}
