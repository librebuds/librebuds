// LibreBuds - SPDX-License-Identifier: GPL-3.0-or-later
// Capability-driven rows provided by PairFree (GPLv3) using LibreBuds' UI style.
package io.github.librebuds.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab

private fun pretty(name: String): String = name.replace('_', ' ')
    .replace('-', ' ').replaceFirstChar { it.uppercase() }

/**
 * Rendering is protocol agnostic: PairFree controls are shown only after the
 * corresponding driver has offered the setting. These cards share the existing
 * QuickBuds-inspired appearance and never touch Huawei's settings.
 */
@Composable
fun VendorSettingsSections(
    groups: List<SettingGroup>,
    tab: SettingTab,
    onChange: (SettingChange) -> Unit,
    modifier: Modifier = Modifier
) {
    val relevant = groups.filter { it.tab == tab && it.key != "power" }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        relevant.forEach { group ->
            val displayable = group.settings.filter { setting ->
                when (setting) {
                    is Setting.Action -> false // Never run a hazardous operation without explicit confirmation.
                    is Setting.Text -> false // Needs validated entry and custom input UI.
                    else -> true
                }
            }
            if (displayable.isEmpty()) return@forEach
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(pretty(group.key), style = MaterialTheme.typography.titleSmall)
                    displayable.forEach { setting ->
                        when (setting) {
                            is Setting.Toggle -> {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(pretty(setting.key),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2
                                    )
                                    Switch(
                                        checked = setting.on,
                                        enabled = setting.enabled,
                                        onCheckedChange = { onChange(SettingChange.Toggle(setting.key, it)) }
                                    )
                                }
                            }
                            is Setting.Choice -> {
                                Text(pretty(setting.key), style = MaterialTheme.typography.bodyMedium)
                                // Filter chips wrap for models with many options or narrow screens.
                                androidx.compose.foundation.layout.FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    setting.options.forEach { option ->
                                        FilterChip(
                                            selected = setting.value == option,
                                            onClick = { onChange(SettingChange.Choice(setting.key, option)) },
                                            label = { Text(pretty(option), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        )
                                    }
                                }
                            }
                            is Setting.Level -> {
                                var pending by remember(setting.key, setting.value) {
                                    mutableFloatStateOf(setting.value.toFloat())
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(pretty(setting.key), style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f))
                                    Text(pending.toInt().toString(), style = MaterialTheme.typography.labelMedium)
                                }
                                Slider(
                                    value = pending.coerceIn(setting.min.toFloat(), setting.max.toFloat()),
                                    onValueChange = { pending = it },
                                    onValueChangeFinished = {
                                        onChange(SettingChange.Level(setting.key,
                                            pending.toInt().coerceIn(setting.min, setting.max)))
                                    },
                                    valueRange = setting.min.toFloat()..setting.max.toFloat(),
                                    enabled = setting.enabled && setting.max > setting.min
                                )
                            }
                            is Setting.Information -> {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(pretty(setting.key), modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text(setting.value, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            is Setting.Action, is Setting.Text -> Unit
                        }
                    }
                }
            }
        }
    }
}
