// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.librebuds.R
import io.github.librebuds.protocol.command.Feature
import io.github.librebuds.state.SettingChange
import io.github.librebuds.ui.model.SettingsModel

/** At-a-glance actions backed by the same real device commands as their full settings rows. */
@Composable
fun QuickActionsPanel(
    model: SettingsModel,
    onChange: (SettingChange) -> Unit,
    onOpenMultipoint: () -> Unit
) {
    val shows = model.wear != null || model.lowLatency != null ||
        model.multipointEnabled != null || model.hdCall != null ||
        Feature.SINGLE_BUD_ANC in model.switches
    if (!shows) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Quick controls",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            model.wear?.let { enabled ->
                FilterChip(
                    selected = enabled,
                    onClick = { onChange(SettingChange.Wear(!enabled)) },
                    label = { Text(stringResource(R.string.wear_detection)) }
                )
            }
            model.lowLatency?.let { enabled ->
                FilterChip(
                    selected = enabled,
                    onClick = { onChange(SettingChange.LowLatencyChange(!enabled)) },
                    label = { Text(stringResource(R.string.low_latency)) }
                )
            }
            model.hdCall?.let { enabled ->
                FilterChip(
                    selected = enabled,
                    onClick = { onChange(SettingChange.HdCallChange(!enabled)) },
                    label = { Text(stringResource(R.string.hd_call)) }
                )
            }
            model.switches[Feature.SINGLE_BUD_ANC]?.let { enabled ->
                FilterChip(
                    selected = enabled,
                    onClick = {
                        onChange(SettingChange.FeatureValue(Feature.SINGLE_BUD_ANC, if (enabled) 0 else 1))
                    },
                    label = { Text(stringResource(R.string.single_bud_anc)) }
                )
            }
            model.multipointEnabled?.let {
                AssistChip(onClick = onOpenMultipoint, label = { Text(stringResource(R.string.multipoint)) })
            }
        }
    }
}
