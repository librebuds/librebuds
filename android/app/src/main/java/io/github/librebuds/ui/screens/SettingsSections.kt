// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.librebuds.R
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.state.SettingChange
import io.github.librebuds.ui.SettingsUi
import io.github.librebuds.ui.components.StyledBottomSheet
import io.github.librebuds.ui.components.StyledList
import io.github.librebuds.ui.components.StyledListItem
import io.github.librebuds.ui.components.StyledListScope
import io.github.librebuds.ui.components.StyledToggle
import io.github.librebuds.ui.model.GestureControl
import io.github.librebuds.ui.model.OptionGroup
import io.github.librebuds.ui.model.Picker
import io.github.librebuds.ui.model.keyOf
import io.github.librebuds.ui.model.optionLabelRes

/** An open picker sheet: its title, choices and what choosing one sends. */
private class PickerRequest(val title: String, val picker: Picker, val onSelect: (Int) -> Unit)

/**
 * The per-model settings below noise control, generated from the profile and the device's values
 * (see [io.github.librebuds.ui.model.settingsModel]). A section is marked experimental when any
 * capability it shows has no verified date in the profile.
 */
@Composable
fun DeviceSettingsSections(ui: SettingsUi, onChange: (SettingChange) -> Unit, onOpenMultipoint: () -> Unit) {
    val model = ui.model
    var request by remember { mutableStateOf<PickerRequest?>(null) }
    val open: (PickerRequest) -> Unit = { request = it }
    val experimental = stringResource(R.string.experimental)
    fun note(vararg capabilities: String) = experimental.takeIf { capabilities.any { it in model.experimental } }

    if (model.gestures.isNotEmpty()) {
        StyledList(title = stringResource(R.string.section_gestures), description = note("gestures")) {
            model.gestures.forEach { GestureRows(it, onChange, open) }
        }
    }
    model.wear?.let { wear ->
        StyledList(title = stringResource(R.string.section_wear), description = note("wear")) {
            StyledToggle(
                label = stringResource(R.string.wear_detection),
                checked = wear,
                onCheckedChange = { onChange(SettingChange.Wear(it)) }
            )
        }
    }
    model.equalizer?.let { picker ->
        StyledList(title = stringResource(R.string.section_equalizer), description = note("equalizer")) {
            PickerRow(stringResource(R.string.equalizer_preset), picker, open) { onChange(SettingChange.EqualizerPreset(it)) }
        }
    }
    if (model.hasSound) {
        // The sound controls come from different capabilities, so each one carries its own note.
        val experimentalShort = stringResource(R.string.experimental_short)
        val withExperimental = stringResource(R.string.with_experimental)
        fun described(capability: String, value: String?): String? = when {
            capability !in model.experimental -> value
            value == null -> experimentalShort
            else -> withExperimental.format(value)
        }
        if (model.lowLatency != null || model.language != null) {
            StyledList(title = stringResource(R.string.section_sound)) {
                model.lowLatency?.let { enabled ->
                    StyledToggle(
                        label = stringResource(if (model.dynamicLatency) R.string.dynamic_latency else R.string.low_latency),
                        description = described("lowLatency", null),
                        checked = enabled,
                        onCheckedChange = { onChange(SettingChange.LowLatencyChange(it)) }
                    )
                }
                // Read-only: language writes are out of scope.
                model.language?.let { language ->
                    StyledListItem(
                        name = stringResource(R.string.voice_language),
                        description = described("language", language.current ?: stringResource(R.string.not_reported))
                    )
                }
            }
        }
        // Its own group so the note about switching sits right under it.
        model.soundQuality?.let { picker ->
            StyledList(title = stringResource(R.string.section_sound).takeIf { model.lowLatency == null && model.language == null }) {
                PickerRow(stringResource(R.string.audio_priority), picker, open, describe = { described("soundQuality", it) }) {
                    onChange(SettingChange.SoundQualityChange(it))
                }
            }
            // Below the row in both design systems (a list description sits above the items in Material).
            Text(
                text = stringResource(R.string.sound_quality_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
    model.multipointEnabled?.let { enabled ->
        StyledList(title = stringResource(R.string.section_multipoint), description = note("multipoint")) {
            StyledListItem(
                name = stringResource(R.string.multipoint),
                description = stringResource(if (enabled) R.string.on else R.string.off),
                onClick = onOpenMultipoint
            )
        }
    }

    PickerSheet(request) { request = null }
}

/**
 * The noise cancellation level row shown under the noise-control modes, with its own picker
 * sheet. [picker] comes from [io.github.librebuds.ui.model.cancellationLevelPicker].
 */
@Composable
fun CancellationLevelList(picker: Picker, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val title = stringResource(R.string.cancellation_level)
    StyledList {
        PickerRow(title, picker, { open = true }, onSelect = onSelect)
    }
    // Built from the live picker on every recomposition, so the check follows the earbuds' level
    // (a request captured when the row was tapped would keep showing the old one).
    PickerSheet(if (open) PickerRequest(title, picker, onSelect) else null) { open = false }
}

@Composable
private fun StyledListScope.GestureRows(
    control: GestureControl,
    onChange: (SettingChange) -> Unit,
    open: (PickerRequest) -> Unit,
) {
    val name = stringResource(control.gesture.nameRes())
    val g = control.gesture
    val sided = control.right != null
    control.left?.let { picker ->
        val title = if (sided) stringResource(R.string.gesture_side, name, stringResource(R.string.left)) else name
        PickerRow(title, picker, open) { onChange(SettingChange.GestureChange(g, left = it, right = null, inCall = null)) }
    }
    control.right?.let { picker ->
        val title = stringResource(R.string.gesture_side, name, stringResource(R.string.right))
        PickerRow(title, picker, open) { onChange(SettingChange.GestureChange(g, left = null, right = it, inCall = null)) }
    }
    control.inCall?.let { picker ->
        val title = stringResource(R.string.gesture_side, name, stringResource(R.string.in_call))
        PickerRow(title, picker, open) { onChange(SettingChange.GestureChange(g, left = null, right = null, inCall = it)) }
    }
}

@Composable
private fun StyledListScope.PickerRow(
    title: String,
    picker: Picker,
    open: (PickerRequest) -> Unit,
    describe: (String?) -> String? = { it },
    onSelect: (Int) -> Unit,
) {
    StyledListItem(
        name = title,
        description = describe(picker.current?.let { optionLabel(picker, it) }),
        onClick = { open(PickerRequest(title, picker, onSelect)) }
    )
}

/** Bottom sheet listing a picker's options with the current one checked. */
@Composable
private fun PickerSheet(request: PickerRequest?, onDismiss: () -> Unit) {
    StyledBottomSheet(visible = request != null, onDismiss = onDismiss, backdrop = rememberLayerBackdrop()) { _, _ ->
        val current = request ?: return@StyledBottomSheet
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp)) {
            Text(
                text = current.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
            )
            StyledList {
                current.picker.options.forEach { option ->
                    StyledListItem(
                        name = optionLabel(current.picker, option.code),
                        selected = option.code == current.picker.current,
                        onClick = {
                            current.onSelect(option.code)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

/**
 * The localized name of [code]'s semantic key, or "Option <code>" ("Level <code>" for a
 * cancellation level) when the profile or this app does not name it.
 */
@Composable
private fun optionLabel(picker: Picker, code: Int): String =
    optionLabelRes(picker.group, picker.keyOf(code))?.let { stringResource(it) }
        ?: stringResource(if (picker.group == OptionGroup.CANCELLATION_LEVEL) R.string.level_code else R.string.option_code, code)

@StringRes
private fun Gesture.nameRes(): Int = when (this) {
    Gesture.DOUBLE_TAP -> R.string.gesture_double_tap
    Gesture.TRIPLE_TAP -> R.string.gesture_triple_tap
    Gesture.LONG_PRESS -> R.string.gesture_long_press
    Gesture.NOISE_CYCLE -> R.string.gesture_noise_cycle
    Gesture.SWIPE -> R.string.gesture_swipe
}
