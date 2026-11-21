// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.theme

import androidx.compose.ui.graphics.Color

/** User-selectable accent schemes, independently implemented for LibreBuds. */
enum class AccentPreset(val label: String, val dark: Color, val light: Color) {
    MINT("Mint", Color(0xFF79D3CD), Color(0xFF006B66)),
    BLUE("Ocean blue", Color(0xFF91BBFF), Color(0xFF235DB5)),
    VIOLET("Violet", Color(0xFFC2ADFF), Color(0xFF6650A4)),
    CORAL("Coral", Color(0xFFFFADA3), Color(0xFFB44236))
}
