// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
// Compact QuickBuds-inspired mode selector, independently written in Compose.
package io.github.librebuds.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.librebuds.R
import io.github.librebuds.ui.model.NoiseControlMode

private data class ModeOption(
    val mode: NoiseControlMode,
    @StringRes val label: Int,
    @DrawableRes val icon: Int
)

@Composable
fun NoiseControlSettings(
    selected: NoiseControlMode?,
    modes: List<NoiseControlMode>,
    onSelected: (NoiseControlMode) -> Unit
) {
    val options = modes.map { mode ->
        when (mode) {
            NoiseControlMode.OFF -> ModeOption(mode, R.string.off, R.drawable.ic_mode_off)
            NoiseControlMode.NOISE_CANCELLATION -> ModeOption(mode, R.string.noise_cancellation, R.drawable.ic_mode_cancellation)
            NoiseControlMode.AWARENESS -> ModeOption(mode, R.string.awareness, R.drawable.ic_mode_awareness)
        }
    }
    if (options.isEmpty()) return
    val colors = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = stringResource(R.string.noise_control),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(colors.surfaceContainerHigh)
                .padding(5.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                val active = selected == option.mode
                val fill = animateColorAsState(
                    targetValue = if (active) colors.primaryContainer else colors.surfaceContainerHigh,
                    animationSpec = tween(220),
                    label = "ANC selection"
                )
                val ink = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(fill.value)
                        .selectable(
                            selected = active,
                            role = Role.RadioButton,
                            onClick = { if (!active) onSelected(option.mode) }
                        )
                        .padding(horizontal = 3.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(option.icon),
                        contentDescription = null,
                        tint = ink,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(option.label),
                        style = MaterialTheme.typography.labelSmall,
                        color = ink,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
