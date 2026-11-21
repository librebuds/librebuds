// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.librebuds.R
import io.github.librebuds.state.SettingChange
import io.github.librebuds.ui.model.SettingsModel
import io.github.librebuds.ui.screens.optionLabel

private data class Shortcut(
    val name: String,
    val detail: String,
    val active: Boolean,
    val onClick: () -> Unit
)

/** Everyday controls only. Other toggles remain available in the Controls screen. */
@Composable
fun QuickActionsPanel(
    model: SettingsModel,
    onChange: (SettingChange) -> Unit,
    onOpenMultipoint: () -> Unit
) {
    var audioPickerOpen by remember { mutableStateOf(false) }
    val entries = buildList {
        model.wear?.let { enabled ->
            add(Shortcut(
                name = stringResource(R.string.quick_wear_detection),
                detail = stringResource(if (enabled) R.string.on else R.string.off),
                active = enabled,
                onClick = { onChange(SettingChange.Wear(!enabled)) }
            ))
        }
        model.lowLatency?.let { enabled ->
            add(Shortcut(
                name = stringResource(if (model.dynamicLatency) R.string.dynamic_latency else R.string.low_latency),
                detail = stringResource(if (enabled) R.string.on else R.string.off),
                active = enabled,
                onClick = { onChange(SettingChange.LowLatencyChange(!enabled)) }
            ))
        }
        model.soundQuality?.let { picker ->
            add(Shortcut(
                name = stringResource(R.string.audio_priority),
                detail = picker.current?.let { optionLabel(picker, it) } ?: stringResource(R.string.not_reported),
                active = false,
                onClick = { audioPickerOpen = true }
            ))
        }
        model.multipointEnabled?.let {
            add(Shortcut(
                name = stringResource(R.string.quick_multipoint),
                detail = stringResource(R.string.quick_manage),
                active = false,
                onClick = onOpenMultipoint
            ))
        }
    }
    if (entries.isEmpty()) return

    val colors = MaterialTheme.colorScheme
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
                rowEntries.forEach { item ->
                    Surface(
                        modifier = Modifier.weight(1f).height(84.dp).clickable(onClick = item.onClick),
                        shape = RoundedCornerShape(28.dp),
                        color = if (item.active) colors.primaryContainer else colors.surfaceContainerLow,
                        contentColor = if (item.active) colors.onPrimaryContainer else colors.onSurface
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.labelMediumEmphasized,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.active) colors.onPrimaryContainer.copy(alpha = 0.8f)
                                    else colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (rowEntries.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    val soundQuality = model.soundQuality
    StyledBottomSheet(
        visible = audioPickerOpen && soundQuality != null,
        onDismiss = { audioPickerOpen = false },
        backdrop = rememberLayerBackdrop()
    ) { _, _ ->
        val picker = soundQuality ?: return@StyledBottomSheet
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.audio_priority),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            StyledList {
                picker.options.forEach { option ->
                    StyledListItem(
                        name = optionLabel(picker, option.code),
                        selected = picker.current == option.code,
                        onClick = {
                            onChange(SettingChange.SoundQualityChange(option.code))
                            audioPickerOpen = false
                        }
                    )
                }
            }
        }
    }
}
