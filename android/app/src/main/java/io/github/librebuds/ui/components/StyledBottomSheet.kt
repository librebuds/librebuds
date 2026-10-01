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
    Modified for LibreBuds (2026): adapted to FreeBuds, opaque surface and scrim; see NOTICE.
*/
package io.github.librebuds.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: LayerBackdrop,
    content: @Composable (innerBackdrop: LayerBackdrop, progress: Float) -> Unit
) {
    if (!visible) return

    val isDarkTheme = isSystemInDarkTheme()
    val sheetState = rememberModalBottomSheetState(false) // move this to parent composable

    val isExpanded =  sheetState.targetValue == SheetValue.Expanded

    val progress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        label = "sheetProgress"
    )

    val animatedCorner = lerp(48.dp, 42.dp, progress)
    val surface = (if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)).copy(alpha = 0.97f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = { },
        shape = RoundedCornerShape(animatedCorner),
        scrimColor = Color.Black.copy(alpha = 0.32f),
        modifier = Modifier.padding(4.dp)
    ) {
        val innerBackdrop = rememberLayerBackdrop()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(animatedCorner))
                .drawBackdrop(
                    backdrop = backdrop,
                    exportedBackdrop = innerBackdrop,
                    shape = { RoundedCornerShape(animatedCorner) },
                    effects = {
                        vibrancy()
                        blur(4f.dp.toPx())
                        lens(12f.dp.toPx(), 48f.dp.toPx(), true)
                    },
                    // Nearly opaque: the backdrop callers pass is not a layer of the screen behind, so a
                    // translucent surface let that screen's text show through the sheet.
                    onDrawSurface = { drawRect(surface) }
                )
                .padding(top = 24.dp)
                .padding(horizontal = 16.dp)
        ) {
            content(innerBackdrop, progress)
        }
    }
}
