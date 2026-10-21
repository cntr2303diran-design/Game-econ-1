package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Neutral Backgrounds & Surfaces (Light Mode)
val NeutralLightBg = Color(0xFFF6F6F4)
val NeutralLightSurface = Color(0xFFFFFFFF)
val NeutralLightSurfaceVariant = Color(0xFFEEEEEC)
val NeutralLightBorder = Color(0xFFD6D6D2)
val NeutralLightTextPrimary = Color(0xFF141517)
val NeutralLightTextSecondary = Color(0xFF52565E)
val NeutralLightTextMuted = Color(0xFF767B85)

// Neutral Backgrounds & Surfaces (Dark Mode)
val NeutralDarkBg = Color(0xFF101214)
val NeutralDarkSurface = Color(0xFF171A1D)
val NeutralDarkSurfaceVariant = Color(0xFF202428)
val NeutralDarkBorder = Color(0xFF2E3338)
val NeutralDarkTextPrimary = Color(0xFFF1F3F5)
val NeutralDarkTextSecondary = Color(0xFFA6ACB5)
val NeutralDarkTextMuted = Color(0xFF78808A)

// Semantic Colors ONLY (Merah = Bahaya, Kuning = Peringatan, Hijau = Membaik, Biru = Aksi Utama)
val SemanticBlueLight = Color(0xFF185ABC)
val SemanticBlueDark = Color(0xFF5897FB)
val SemanticBlueBgLight = Color(0xFFE8F0FE)
val SemanticBlueBgDark = Color(0xFF162742)

val SemanticRedLight = Color(0xFFC5221F)
val SemanticRedDark = Color(0xFFF28B82)
val SemanticRedBgLight = Color(0xFFFCE8E6)
val SemanticRedBgDark = Color(0xFF3C1919)

val SemanticYellowLight = Color(0xFFB06000)
val SemanticYellowDark = Color(0xFFFDD663)
val SemanticYellowBgLight = Color(0xFFFEF7E0)
val SemanticYellowBgDark = Color(0xFF362A10)

val SemanticGreenLight = Color(0xFF137333)
val SemanticGreenDark = Color(0xFF81C995)
val SemanticGreenBgLight = Color(0xFFE6F4EA)
val SemanticGreenBgDark = Color(0xFF13311E)

// Helper semantic color accessors that automatically adapt to Light/Dark mode
object KapitalSemanticColors {
    val isDark: Boolean
        @Composable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val blue: Color
        @Composable get() = if (isDark) SemanticBlueDark else SemanticBlueLight
    val blueContainer: Color
        @Composable get() = if (isDark) SemanticBlueBgDark else SemanticBlueBgLight

    val red: Color
        @Composable get() = if (isDark) SemanticRedDark else SemanticRedLight
    val redContainer: Color
        @Composable get() = if (isDark) SemanticRedBgDark else SemanticRedBgLight

    val yellow: Color
        @Composable get() = if (isDark) SemanticYellowDark else SemanticYellowLight
    val yellowContainer: Color
        @Composable get() = if (isDark) SemanticYellowBgDark else SemanticYellowBgLight

    val green: Color
        @Composable get() = if (isDark) SemanticGreenDark else SemanticGreenLight
    val greenContainer: Color
        @Composable get() = if (isDark) SemanticGreenBgDark else SemanticGreenBgLight

    val grayText: Color
        @Composable get() = if (isDark) NeutralDarkTextSecondary else NeutralLightTextSecondary
    val border: Color
        @Composable get() = if (isDark) NeutralDarkBorder else NeutralLightBorder
}
