package com.ryo.androidfilemanager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ryo.androidfilemanager.R

// ウェイトごとに静的フォントを 4 ファイル同梱すると 15 MB 近くなる。
// 可変フォント 1 本（約 3.6 MB）を wght 軸で使い分けることでサイズを抑える。
// variationSettings は ExperimentalTextApi のためオプトインが必要。
@OptIn(ExperimentalTextApi::class)
private val MPlus2 = FontFamily(
    Font(
        R.font.mplus2,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.mplus2,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.mplus2,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.mplus2,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

// Material3 既定の 15 スタイルのサイズ・行間・字間はそのまま流用し、フォントだけ差し替える。
// 日本語フォントは字面が大きく見えがちだが、今回はまず既定値で運用し必要になれば個別に調整する。
val AppTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = MPlus2),
        displayMedium = base.displayMedium.copy(fontFamily = MPlus2),
        displaySmall = base.displaySmall.copy(fontFamily = MPlus2),
        headlineLarge = base.headlineLarge.copy(fontFamily = MPlus2),
        headlineMedium = base.headlineMedium.copy(fontFamily = MPlus2),
        headlineSmall = base.headlineSmall.copy(fontFamily = MPlus2),
        titleLarge = base.titleLarge.copy(fontFamily = MPlus2),
        titleMedium = base.titleMedium.copy(fontFamily = MPlus2),
        titleSmall = base.titleSmall.copy(fontFamily = MPlus2),
        bodyLarge = base.bodyLarge.copy(fontFamily = MPlus2),
        bodyMedium = base.bodyMedium.copy(fontFamily = MPlus2),
        bodySmall = base.bodySmall.copy(fontFamily = MPlus2),
        labelLarge = base.labelLarge.copy(fontFamily = MPlus2),
        labelMedium = base.labelMedium.copy(fontFamily = MPlus2),
        labelSmall = base.labelSmall.copy(fontFamily = MPlus2),
    )
}
