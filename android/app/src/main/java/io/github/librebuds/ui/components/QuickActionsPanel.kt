// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.librebuds.R
import io.github.librebuds.protocol.command.Feature
import io.github.librebuds.state.SettingChange
import io.github.librebuds.ui.model.SettingsModel

private data class Shortcut(
    val name: String,
    val active: Boolean?,
    val action: () -> Unit
)

/**
 * Two-column quick settings grid. Unlike the old overflowing horizontal chip strip,
 * every available device setting stays visible at typical phone widths.
 *
 * Capability gating, hardware values and the commands remain owned by SettingsModel.
 */
@Composable
fun QuickActionsPanel(
    model: SettingsModel,
    onChange: (SettingChange) -> Unit,
    onOpenMultipoint: () -> Unit
) {
    val entries = buildList {
        model.wear?.let { enabled ->
            add(Shortcut(stringResource(R.string.wear_detection), enabled) {
                onChange(SettingChange.Wear(!enabled))
            })
        }
        model.lowLatency?.let { enabled ->
            add(Shortcut(stringResource(if (model.dynamicLatency) R.string.dynamic_latency else R.string.low_latency), enabled) {
                onChange(SettingChange.LowLatencyChange(!enabled))
            })
        }
        model.hdCall?.let { enabled ->
            add(Shortcut(stringResource(R.string.hd_call), enabled) {
                onChange(SettingChange.HdCallChange(!enabled))
            })
        }
        model.switches[Feature.SINGLE_BUD_ANC]?.let { enabled ->
            add(Shortcut(stringResource(R.string.single_bud_anc), enabled) {
                onChange(SettingChange.FeatureValue(Feature.SINGLE_BUD_ANC, if (enabled) 0 else 1))
            })
        }
        model.multipointEnabled?.let { enabled ->
            add(Shortcut(stringResource(R.string.multipoint), enabled, onOpenMultipoint))
        }
    }
    if (entries.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.device_quick_actions),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        entries.chunked(2).forEach { rowEntries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowEntries.forEach { shortcut ->
                    val colors = MaterialTheme.colorScheme
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 66.dp).clickable(onClick = shortcut.action),
                        color = if (shortcut.active == true) colors.primaryContainer else colors.surfaceContainerLow,
                        contentColor = if (shortcut.active == true) colors.onPrimaryContainer else colors.onSurface,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = shortcut.name,
                                style = MaterialTheme.typography.labelMediumEmphasized,
                                maxLines = 2
                            )
                            Text(
                                text = when (shortcut.active) {
                                    true -> stringResource(R.string.on)
                                    false -> stringResource(R.string.off)
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (shortcut.active == true) colors.onPrimaryContainer.copy(alpha = 0.78f)
                                    else colors.onSurfaceVariant
                            )
                        }
                    }
                }
                if (rowEntries.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
