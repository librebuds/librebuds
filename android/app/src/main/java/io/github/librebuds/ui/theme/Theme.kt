/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
    Modified for LibreBuds (2026): adapted to FreeBuds; see NOTICE.
*/

package io.github.librebuds.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val ColorScheme.sectionHeader: Color
    get() = onBackground.copy(alpha = 0.6f)

// QuickBuds-inspired neutral palette: true black in AMOLED mode, charcoal panels otherwise.
private val QuickDark = darkColorScheme(
    primary = Color(0xFF79D3CD),
    onPrimary = Color(0xFF003936),
    primaryContainer = Color(0xFF23514F),
    onPrimaryContainer = Color(0xFFD1F7F2),
    background = Color(0xFF0E1013),
    onBackground = Color(0xFFF4F5F7),
    surface = Color(0xFF191C20),
    onSurface = Color(0xFFF4F5F7),
    surfaceContainer = Color(0xFF0E1013),
    surfaceContainerLow = Color(0xFF171A1E),
    surfaceContainerHigh = Color(0xFF25292F),
    surfaceContainerHighest = Color(0xFF2C3137),
    outlineVariant = Color(0xFF454A50),
    onSurfaceVariant = Color(0xFFAFB8BD),
    secondaryContainer = Color(0xFF294743),
    onSecondaryContainer = Color(0xFFD3F3EC)
)

private val QuickLight = lightColorScheme(
    primary = Color(0xFF006B66),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBEDE7),
    onPrimaryContainer = Color(0xFF003D39),
    background = Color(0xFFF3F5F6),
    onBackground = Color(0xFF161A1D),
    surface = Color.White,
    onSurface = Color(0xFF161A1D),
    surfaceContainer = Color(0xFFF3F5F6),
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFE7ECEE),
    surfaceContainerHighest = Color(0xFFDCE5E6),
    outlineVariant = Color(0xFFC2CFD1),
    onSurfaceVariant = Color(0xFF536063),
    secondaryContainer = Color(0xFFCCEAE5)
)

@Composable
fun LibreBudsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledBlack: Boolean = false,
    accentPreset: AccentPreset = AccentPreset.MINT,
    content: @Composable () -> Unit
) {
    val initial = if (darkTheme) QuickDark else QuickLight
    val accent = if (darkTheme) accentPreset.dark else accentPreset.light
    val base = initial.copy(primary = accent, primaryContainer = accent.copy(alpha = 0.25f), onPrimaryContainer = initial.onSurface)
    val colorScheme = if (darkTheme && amoledBlack) base.copy(
        background = Color.Black,
        surfaceContainer = Color.Black,
        surface = Color(0xFF0B0D0F),
        surfaceContainerLow = Color(0xFF101215),
        surfaceContainerHigh = Color(0xFF1B1E22),
        surfaceContainerHighest = Color(0xFF262A2F)
    ) else base

    CompositionLocalProvider(
        LocalDesignSystem provides DesignSystem.Material
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = MaterialTypography,
            content = content
        )
    }
}
