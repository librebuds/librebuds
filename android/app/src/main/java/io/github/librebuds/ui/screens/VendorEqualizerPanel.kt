// LibreBuds - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.librebuds.ui.components.EqualizerCurve
import io.github.omegadestroyer.pairfree.eq.EqProfile
import io.github.omegadestroyer.pairfree.eq.EqState
import kotlin.math.roundToInt

/**
 * PairFree handles the vendor-specific EQ frames and persistence; this panel
 * reuses LibreBuds' QuickBuds-style live EQ curve and rounded controls.
 */
@Composable
fun VendorEqualizerPanel(
    state: EqState,
    onSelect: (EqProfile) -> Unit,
    onGain: (Int, Double) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Equalizer", style = MaterialTheme.typography.titleMedium)
            if (!state.target.confirmed) {
                Text(
                    "This model's equalizer commands have not been verified. Editing is disabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                state.profiles.take(24).forEach { profile ->
                    FilterChip(
                        selected = state.selected?.id == profile.id,
                        enabled = state.target.confirmed,
                        onClick = { onSelect(profile) },
                        label = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
            if (state.target.hasCustom && state.layout.bands.isNotEmpty()) {
                val range = state.layout.userRangeDb.coerceAtLeast(1)
                EqualizerCurve(
                    gains = state.curve.map { it.roundToInt() },
                    minGain = -range,
                    maxGain = range
                )
                state.layout.bands.forEachIndexed { index, band ->
                    val current = state.curve.getOrElse(index) { 0.0 }
                        .coerceIn(-range.toDouble(), range.toDouble())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (band.hz >= 1000) "${band.hz / 1000.0}k Hz" else "${band.hz} Hz",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text("${(current * 10).roundToInt() / 10.0} dB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val step = state.target.gainStepDb.coerceAtLeast(0.5)
                    Slider(
                        value = current.toFloat(),
                        onValueChange = { raw ->
                            val snapped = (raw.toDouble() / step).roundToInt() * step
                            onGain(index, snapped.coerceIn(-range.toDouble(), range.toDouble()))
                        },
                        valueRange = -range.toFloat()..range.toFloat(),
                        enabled = state.target.confirmed
                    )
                }
            }
        }
    }
}
