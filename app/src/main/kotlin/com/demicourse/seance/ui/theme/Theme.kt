package com.demicourse.seance.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.demicourse.domain.ThemeChoice

/**
 * Color roles mirroring the prototype's CSS custom properties 1:1 (see the `:root` and
 * `:root[data-theme=light]` blocks in Séance.dc.html), rather than being force-fit into
 * Material3's color-scheme slots — this keeps the port visually exact.
 */
data class SeanceColors(
    val pageBg: Color,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val field: Color,
    val fieldDeep: Color,
    val line: Color,
    val line2: Color,
    val line3: Color,
    val dash: Color,
    val fg: Color,
    val fg2: Color,
    val fg3: Color,
    val muted: Color,
    val muted2: Color,
    val muted3: Color,
    val accent: Color,
    val accentHover: Color,
    val onAccent: Color,
    val accentFg: Color,
    val accentSoft: Color,
    val accentSoftLine: Color,
    val accentChip: Color,
    val accentFaint: Color,
    val danger: Color,
    val dangerSoft: Color,
    val scrim: Color,
    val isDark: Boolean,
)

private val DarkColors = SeanceColors(
    pageBg = Color(0xFF0F0F0C),
    bg = Color(0xFF191812),
    surface = Color(0xFF23221B),
    surface2 = Color(0xFF2B2A22),
    surface3 = Color(0xFF2E2D24),
    field = Color(0xFF1D1C16),
    fieldDeep = Color(0xFF191812),
    line = Color(0xFF2F2E24),
    line2 = Color(0xFF3A3930),
    line3 = Color(0xFF363528),
    dash = Color(0xFF3F3E33),
    fg = Color(0xFFF1EFE4),
    fg2 = Color(0xFFE4E1D4),
    fg3 = Color(0xFFC9C6B8),
    muted = Color(0xFFA3A094),
    muted2 = Color(0xFF8D8B80),
    muted3 = Color(0xFF7D7B70),
    accent = Color(0xFF5AA06B),
    accentHover = Color(0xFF69B079),
    onAccent = Color(0xFF11200E),
    accentFg = Color(0xFF8FC79C),
    accentSoft = Color(0x215AA06B),
    accentSoftLine = Color(0x4D5AA06B),
    accentChip = Color(0x295AA06B),
    accentFaint = Color(0x1A5AA06B),
    danger = Color(0xFFC9705F),
    dangerSoft = Color(0xFF3A2B26),
    scrim = Color(0x9E080806),
    isDark = true,
)

private val LightColors = SeanceColors(
    pageBg = Color(0xFFDEDBCB),
    bg = Color(0xFFF4F2E7),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEAE7D9),
    surface3 = Color(0xFFE2DFCF),
    field = Color(0xFFF7F5EC),
    fieldDeep = Color(0xFFEAE7D9),
    line = Color(0xFFE5E2D2),
    line2 = Color(0xFFD5D2C0),
    line3 = Color(0xFFDCD9C7),
    dash = Color(0xFFC6C2AE),
    fg = Color(0xFF24231B),
    fg2 = Color(0xFF34332A),
    fg3 = Color(0xFF4C4A40),
    muted = Color(0xFF6F6D61),
    muted2 = Color(0xFF7D7A6B),
    muted3 = Color(0xFF95917F),
    accent = Color(0xFF3D7D50),
    accentHover = Color(0xFF336943),
    onAccent = Color(0xFFFFFFFF),
    accentFg = Color(0xFF2F6B41),
    accentSoft = Color(0x1A3D7D50),
    accentSoftLine = Color(0x423D7D50),
    accentChip = Color(0x243D7D50),
    accentFaint = Color(0x123D7D50),
    danger = Color(0xFFA8452F),
    dangerSoft = Color(0xFFF3DED7),
    scrim = Color(0x61242318),
    isDark = false,
)

val LocalSeanceColors = compositionLocalOf { DarkColors }

@Composable
fun resolveIsDark(themeChoice: ThemeChoice): Boolean = when (themeChoice) {
    ThemeChoice.SYSTEM -> isSystemInDarkTheme()
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK -> true
}

@Composable
fun SeanceTheme(themeChoice: ThemeChoice, content: @Composable () -> Unit) {
    val isDark = resolveIsDark(themeChoice)
    val colors = if (isDark) DarkColors else LightColors

    // The window is edge-to-edge (see MainActivity), so the system bars sit on top of our own
    // background: their icons have to follow the *app's* theme choice, not the system's.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalSeanceColors provides colors, content = content)
}
