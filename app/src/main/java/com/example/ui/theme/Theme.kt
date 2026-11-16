package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class VideoStarExtraColors(
    val neonPink: Color,
    val logoNavy: Color,
    val popoverBackground: Color,
    val popoverDivider: Color,
    val toolbarBackground: Color,
    val activeTabSurface: Color,
    val darkButtonSurface: Color,
    val patternLine: Color,
    val hintText: Color
)

val LocalVideoStarColors = staticCompositionLocalOf {
    VideoStarExtraColors(
        neonPink = VideoStarNeonPink,
        logoNavy = VideoStarLogoNavy,
        popoverBackground = VideoStarPopoverBackground,
        popoverDivider = VideoStarPopoverDivider,
        toolbarBackground = VideoStarLightToolbar,
        activeTabSurface = VideoStarLightActiveTab,
        darkButtonSurface = VideoStarDarkButton,
        patternLine = VideoStarPatternLine,
        hintText = VideoStarInkMuted
    )
}

private val VideoStarLightColorScheme = lightColorScheme(
    primary = VideoStarMagenta,
    onPrimary = VideoStarWhite,
    primaryContainer = VideoStarLightActiveTab,
    onPrimaryContainer = VideoStarMagenta,
    secondary = VideoStarLogoNavy,
    onSecondary = VideoStarWhite,
    tertiary = VideoStarNeonPink,
    onTertiary = VideoStarWhite,
    background = VideoStarLightSurface,
    onBackground = VideoStarInkDark,
    surface = VideoStarLightSurface,
    onSurface = VideoStarInkDark,
    surfaceVariant = VideoStarLightSurfaceVariant,
    onSurfaceVariant = VideoStarInkMuted,
    outline = VideoStarBlack
)

private val VideoStarDarkColorScheme = darkColorScheme(
    primary = VideoStarNeonPink,
    onPrimary = VideoStarWhite,
    primaryContainer = VideoStarMagenta,
    onPrimaryContainer = VideoStarWhite,
    secondary = VideoStarMagenta,
    onSecondary = VideoStarWhite,
    tertiary = VideoStarNeonPink,
    onTertiary = VideoStarWhite,
    background = VideoStarDarkBackground,
    onBackground = VideoStarWhite,
    surface = VideoStarDarkSurface,
    onSurface = VideoStarWhite,
    surfaceVariant = VideoStarDarkButton,
    onSurfaceVariant = VideoStarDarkHintText,
    outline = VideoStarDarkBorder
)

@Composable
fun VideoStarTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VideoStarDarkColorScheme else VideoStarLightColorScheme
    val extraColors = if (darkTheme) {
        VideoStarExtraColors(
            neonPink = VideoStarNeonPink,
            logoNavy = VideoStarLogoNavy,
            popoverBackground = VideoStarPopoverBackground,
            popoverDivider = VideoStarPopoverDivider,
            toolbarBackground = VideoStarDarkSurface,
            activeTabSurface = VideoStarDarkButton,
            darkButtonSurface = VideoStarDarkButton,
            patternLine = VideoStarDarkBorder,
            hintText = VideoStarDarkHintText
        )
    } else {
        VideoStarExtraColors(
            neonPink = VideoStarNeonPink,
            logoNavy = VideoStarLogoNavy,
            popoverBackground = VideoStarPopoverBackground,
            popoverDivider = VideoStarPopoverDivider,
            toolbarBackground = VideoStarLightToolbar,
            activeTabSurface = VideoStarLightActiveTab,
            darkButtonSurface = VideoStarDarkButton,
            patternLine = VideoStarPatternLine,
            hintText = VideoStarInkMuted
        )
    }

    CompositionLocalProvider(LocalVideoStarColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = VideoStarShapes,
            content = content
        )
    }
}
