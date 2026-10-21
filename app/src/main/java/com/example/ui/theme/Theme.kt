package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val KapitalLightColorScheme = lightColorScheme(
    primary = SemanticBlueLight,
    onPrimary = NeutralLightSurface,
    primaryContainer = SemanticBlueBgLight,
    onPrimaryContainer = SemanticBlueLight,
    secondary = NeutralLightTextSecondary,
    onSecondary = NeutralLightSurface,
    secondaryContainer = NeutralLightSurfaceVariant,
    onSecondaryContainer = NeutralLightTextPrimary,
    tertiary = SemanticGreenLight,
    onTertiary = NeutralLightSurface,
    background = NeutralLightBg,
    onBackground = NeutralLightTextPrimary,
    surface = NeutralLightSurface,
    onSurface = NeutralLightTextPrimary,
    surfaceVariant = NeutralLightSurfaceVariant,
    onSurfaceVariant = NeutralLightTextSecondary,
    error = SemanticRedLight,
    onError = NeutralLightSurface,
    errorContainer = SemanticRedBgLight,
    onErrorContainer = SemanticRedLight,
    outline = NeutralLightBorder
)

private val KapitalDarkColorScheme = darkColorScheme(
    primary = SemanticBlueDark,
    onPrimary = NeutralDarkBg,
    primaryContainer = SemanticBlueBgDark,
    onPrimaryContainer = SemanticBlueDark,
    secondary = NeutralDarkTextSecondary,
    onSecondary = NeutralDarkBg,
    secondaryContainer = NeutralDarkSurfaceVariant,
    onSecondaryContainer = NeutralDarkTextPrimary,
    tertiary = SemanticGreenDark,
    onTertiary = NeutralDarkBg,
    background = NeutralDarkBg,
    onBackground = NeutralDarkTextPrimary,
    surface = NeutralDarkSurface,
    onSurface = NeutralDarkTextPrimary,
    surfaceVariant = NeutralDarkSurfaceVariant,
    onSurfaceVariant = NeutralDarkTextSecondary,
    error = SemanticRedDark,
    onError = NeutralDarkBg,
    errorContainer = SemanticRedBgDark,
    onErrorContainer = SemanticRedDark,
    outline = NeutralDarkBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) KapitalDarkColorScheme else KapitalLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
