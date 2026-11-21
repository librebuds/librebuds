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

private val OneUiDarkColorScheme = darkColorScheme(
    surfaceContainer = Color(0xFF000000), // for some reason background is not used as the background in gmail and settings app, but surfacecontainer, so using that
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1D21),
    onSurface = Color(0xFFFFFFFF),
    surfaceDim = Color(0x40888888),
    primary = Color(0xFF4F8CFF),
    secondaryContainer = Color(0xFF263C68),
    onSecondaryContainer = Color(0xFF8FB5FF),
    onPrimary = Color(0xFFFFFFFF)
)

private val OneUiLightColorScheme = lightColorScheme(
    surfaceContainer = Color(0xFFF5F6F8),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceDim = Color(0x40D9D9D9),
    secondaryContainer = Color(0xFFDCE7FF),
    onSecondaryContainer = Color(0xFF2D5FB8),
    primary = Color(0xFF2D6CDF),
    onPrimary = Color(0xFFFFFFFF)
)

@Composable
fun LibreBudsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    m3eEnabled: Boolean = false,
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        m3eEnabled -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> OneUiDarkColorScheme.copy(
            background = if (amoledBlack) Color.Black else OneUiDarkColorScheme.background,
            surface = if (amoledBlack) Color(0xFF090A0C) else OneUiDarkColorScheme.surface,
            surfaceContainer = if (amoledBlack) Color.Black else OneUiDarkColorScheme.surfaceContainer,
            surfaceContainerLow = if (amoledBlack) Color.Black else OneUiDarkColorScheme.surfaceContainerLow,
            surfaceContainerHigh = if (amoledBlack) Color(0xFF111214) else OneUiDarkColorScheme.surfaceContainerHigh,
            surfaceContainerHighest = if (amoledBlack) Color(0xFF18191C) else OneUiDarkColorScheme.surfaceContainerHighest,
        )
        else -> OneUiLightColorScheme
    }

    CompositionLocalProvider(
        LocalDesignSystem provides
            if (m3eEnabled) DesignSystem.Material
            else DesignSystem.Apple
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = if (m3eEnabled) MaterialTypography else AppleTypography,
            content = content
        )
    }
}
