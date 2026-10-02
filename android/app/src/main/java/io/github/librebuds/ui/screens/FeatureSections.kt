// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.librebuds.R
import io.github.librebuds.protocol.command.CustomPreset
import io.github.librebuds.protocol.command.EqOperation
import io.github.librebuds.protocol.command.Equalizer
import io.github.librebuds.protocol.command.Feature
import io.github.librebuds.protocol.command.FeatureSwitch
import io.github.librebuds.protocol.command.PinchSlot
import io.github.librebuds.protocol.command.Side
import io.github.librebuds.state.SettingChange
import io.github.librebuds.ui.components.StyledBottomSheet
import io.github.librebuds.ui.components.StyledButton
import io.github.librebuds.ui.components.StyledInputField
import io.github.librebuds.ui.components.StyledList
import io.github.librebuds.ui.components.StyledListItem
import io.github.librebuds.ui.components.StyledListScope
import io.github.librebuds.ui.components.StyledSlider
import io.github.librebuds.ui.components.StyledToggle
import io.github.librebuds.ui.model.CustomEqualizerModel
import io.github.librebuds.ui.model.SettingsModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The noise-control extras that sit right under the mode buttons, as the vendor app groups them:
 * the awareness sub-mode and adaptive awareness slider while awareness is on, and noise
 * cancelling with one earbud.
 */
@Composable
internal fun NoiseExtrasSection(model: SettingsModel, note: String?, open: (PickerRequest) -> Unit, onChange: (SettingChange) -> Unit) {
    val singleBud = model.switches[Feature.SINGLE_BUD_ANC]
    if (model.awareness == null && model.awarenessLevel == null && singleBud == null) return
    StyledList(title = stringResource(R.string.section_noise_extras), description = note) {
        model.awareness?.let { picker ->
            PickerRow(stringResource(R.string.awareness_mode), picker, open, live = { it.awareness }) { onChange(SettingChange.AwarenessMode(it)) }
        }
        model.awarenessLevel?.let { level ->
            val label = stringResource(R.string.awareness_level)
            item { index, count ->
                CommittingSlider(label, level, 0..10, index, count, format = { it.toString() }) { onChange(SettingChange.AwarenessLevel(it)) }
            }
        }
        singleBud?.let { on ->
            StyledToggle(
                label = stringResource(R.string.single_bud_anc),
                checked = on,
                onCheckedChange = { onChange(SettingChange.FeatureValue(Feature.SINGLE_BUD_ANC, if (it) 1 else 0)) }
            )
        }
    }
}

/** Adaptive volume and conversation awareness, when the earbuds offer them. */
@Composable
internal fun AdaptiveAudioSection(model: SettingsModel, note: String?, onChange: (SettingChange) -> Unit) {
    val rows = listOf(Feature.ADAPTIVE_VOLUME to R.string.adaptive_volume, Feature.AI_CONVERSATION to R.string.ai_conversation)
        .filter { it.first in model.switches }
    if (rows.isEmpty()) return
    StyledList(title = stringResource(R.string.section_adaptive_audio), description = note) {
        rows.forEach { (feature, label) ->
            StyledToggle(
                label = stringResource(label),
                checked = model.switches.getValue(feature),
                onCheckedChange = { onChange(SettingChange.FeatureValue(feature, if (it) 1 else 0)) }
            )
        }
    }
}

/** Pinch slots: each tap slot has one action for both earbuds; pinch and hold one per side. */
@Composable
internal fun PinchSection(model: SettingsModel, note: String?, open: (PickerRequest) -> Unit, onChange: (SettingChange) -> Unit) {
    if (model.pinch.isEmpty()) return
    StyledList(title = stringResource(R.string.section_pinch), description = note) {
        model.pinch.forEach { control ->
            val slot = control.slot
            val name = pinchName(slot)
            control.left?.let { picker ->
                val title = if (control.right != null) stringResource(R.string.gesture_side, name, stringResource(R.string.left)) else name
                PickerRow(title, picker, open, live = { m -> m.pinch.firstOrNull { it.slot == slot }?.left }) { onChange(SettingChange.PinchChange(slot, left = it, right = null)) }
            }
            control.right?.let { picker ->
                val title = stringResource(R.string.gesture_side, name, stringResource(R.string.right))
                PickerRow(title, picker, open, live = { m -> m.pinch.firstOrNull { it.slot == slot }?.right }) { onChange(SettingChange.PinchChange(slot, left = null, right = it)) }
            }
        }
    }
}

@Composable
private fun pinchName(slot: PinchSlot): String {
    val base = stringResource(
        when (slot.type) {
            0 -> R.string.pinch_once
            1 -> R.string.pinch_twice
            2 -> R.string.pinch_three
            else -> R.string.pinch_hold
        }
    )
    return if (slot.scene == 1) stringResource(R.string.pinch_in_call, base) else base
}

/** Head gestures for calls: on/off, then the nod and shake actions while on. */
@Composable
internal fun HeadControlSection(model: SettingsModel, note: String?, open: (PickerRequest) -> Unit, onChange: (SettingChange) -> Unit) {
    val head = model.headControl ?: return
    StyledList(title = stringResource(R.string.section_head_control), description = note) {
        StyledToggle(
            label = stringResource(R.string.head_control),
            checked = head.enabled,
            onCheckedChange = { onChange(SettingChange.FeatureValue(Feature.HEAD_CONTROL, if (it) 1 else 0)) }
        )
        if (head.enabled) {
            head.nod?.let { picker ->
                PickerRow(stringResource(R.string.head_nod), picker, open, live = { it.headControl?.nod }) {
                    onChange(SettingChange.FeatureValue(Feature.HEAD_CONTROL, it, FeatureSwitch.NOD))
                }
            }
            head.shake?.let { picker ->
                PickerRow(stringResource(R.string.head_shake), picker, open, live = { it.headControl?.shake }) {
                    onChange(SettingChange.FeatureValue(Feature.HEAD_CONTROL, it, FeatureSwitch.SHAKE))
                }
            }
        }
    }
}

/** Ear tip type, drop detection and rest reminders, in the wear section. */
@Composable
internal fun StyledListScope.WearExtraRows(model: SettingsModel, open: (PickerRequest) -> Unit, onChange: (SettingChange) -> Unit) {
    model.earTip?.let { picker ->
        PickerRow(stringResource(R.string.ear_tip), picker, open, live = { it.earTip }) { onChange(SettingChange.FeatureValue(Feature.EAR_TIP, it)) }
    }
    model.switches[Feature.DROP_DETECTION]?.let { on ->
        StyledToggle(
            label = stringResource(R.string.drop_detection),
            checked = on,
            onCheckedChange = { onChange(SettingChange.FeatureValue(Feature.DROP_DETECTION, if (it) 1 else 0)) }
        )
    }
    model.restReminder?.let { on ->
        StyledToggle(
            label = stringResource(R.string.rest_reminder),
            checked = on,
            onCheckedChange = { onChange(SettingChange.RestReminderChange(it)) }
        )
    }
}

/** The custom presets row and its sheet: the saved presets, a new one, and the ten-band editor. */
@Composable
internal fun StyledListScope.CustomEqualizerRow(custom: CustomEqualizerModel, onChange: (SettingChange) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val title = stringResource(R.string.custom_presets)
    StyledListItem(
        name = title,
        description = if (custom.presets.isEmpty()) stringResource(R.string.custom_preset_none) else pluralStringResource(R.plurals.custom_preset_count, custom.presets.size, custom.presets.size),
        onClick = { open = true }
    )
    // A sheet is a window of its own, so it can be declared here without taking a place in the list.
    CustomEqualizerSheet(open, title, custom, onChange) { open = false }
}

@Composable
private fun CustomEqualizerSheet(visible: Boolean, title: String, custom: CustomEqualizerModel, onChange: (SettingChange) -> Unit, onDismiss: () -> Unit) {
    // The preset being edited (null: the list of presets).
    var editing by remember(visible) { mutableStateOf<CustomPreset?>(null) }
    StyledBottomSheet(visible = visible, onDismiss = onDismiss, backdrop = rememberLayerBackdrop()) { _, _ ->
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp))
            val preset = editing
            if (preset == null) {
                StyledList {
                    custom.presets.forEach { saved ->
                        StyledListItem(name = saved.name.ifEmpty { saved.id.toString() }, onClick = { editing = saved })
                    }
                    custom.freeSlot?.let { slot ->
                        StyledListItem(
                            name = stringResource(R.string.custom_preset_new),
                            onClick = { editing = CustomPreset(slot, List(Equalizer.BANDS) { 0 }, "") }
                        )
                    }
                }
                if (custom.freeSlot == null) Hint(stringResource(R.string.custom_preset_full))
            } else {
                CustomEqualizerEditor(preset, existing = custom.presets.any { it.id == preset.id }, onChange = onChange, onDone = onDismiss)
            }
        }
    }
}

private val BAND_LABELS = listOf("60", "125", "250", "500", "1k", "2k", "4k", "8k", "12k", "16k")

@Composable
private fun CustomEqualizerEditor(preset: CustomPreset, existing: Boolean, onChange: (SettingChange) -> Unit, onDone: () -> Unit) {
    val name = rememberTextFieldState(preset.name)
    val focus = remember { FocusRequester() }
    val gains = remember(preset.id) { mutableStateListOf(*preset.gains.toTypedArray()) }
    fun currentName() = name.text.toString().trim().take(Equalizer.MAX_NAME_LENGTH).ifEmpty { preset.id.toString() }
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StyledInputField(inputState = name, focusRequester = focus, placeholder = stringResource(R.string.custom_preset_name))
        BAND_LABELS.forEachIndexed { band, label ->
            CommittingSlider(
                label = stringResource(R.string.eq_band_hz, label),
                value = gains[band],
                range = Equalizer.MIN_GAIN..Equalizer.MAX_GAIN,
                format = { if (it > 0) "+$it" else it.toString() },
            ) { value ->
                gains[band] = value
                // The vendor app lets the earbuds play each change before it is saved.
                onChange(SettingChange.CustomEqualizer(preset.id, gains.toList(), currentName(), EqOperation.PREVIEW))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val backdrop = rememberLayerBackdrop()
            StyledButton(
                onClick = {
                    onChange(SettingChange.CustomEqualizer(preset.id, gains.toList(), currentName(), EqOperation.SAVE))
                    onDone()
                },
                backdrop = backdrop,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.custom_preset_save), style = MaterialTheme.typography.labelLarge) }
            if (existing) {
                StyledButton(
                    onClick = {
                        onChange(SettingChange.CustomEqualizer(preset.id, gains.toList(), currentName(), EqOperation.DELETE))
                        onDone()
                    },
                    backdrop = backdrop,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.custom_preset_delete), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/**
 * Find earbuds: one row per side that plays or stops the sound, with the warning to take the
 * earbuds out first. Leaving the screen stops any side still ringing.
 */
@Composable
internal fun FindEarbudsSection(ringing: Map<Side, Boolean>, note: String?, onRing: (Side, Boolean) -> Unit) {
    val currentRinging by rememberUpdatedState(ringing)
    val ring by rememberUpdatedState(onRing)
    DisposableEffect(Unit) {
        onDispose { currentRinging.filterValues { it }.keys.forEach { ring(it, false) } }
    }
    StyledList(title = stringResource(R.string.section_find), description = note ?: stringResource(R.string.find_warning)) {
        listOf(Side.LEFT to R.string.find_left, Side.RIGHT to R.string.find_right).forEach { (side, label) ->
            val on = ringing[side] == true
            StyledListItem(
                name = stringResource(label),
                description = stringResource(if (on) R.string.find_stop else R.string.find_ring),
                onClick = { onRing(side, !on) }
            )
        }
    }
    if (note != null) Hint(stringResource(R.string.find_warning))
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/**
 * A whole-number slider that commits a value once the finger has rested on it for a moment, so a
 * drag sends one change rather than one per step. Follows [value] when the earbuds report another.
 */
@Composable
private fun CommittingSlider(
    label: String,
    value: Int,
    range: IntRange,
    index: Int = 0,
    count: Int = 1,
    format: (Int) -> String,
    onCommit: (Int) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val commit by rememberUpdatedState(onCommit)
    val shown = local.roundToInt()
    LaunchedEffect(shown) {
        if (shown == value) return@LaunchedEffect
        delay(COMMIT_DELAY_MILLIS)
        commit(shown)
    }
    StyledSlider(
        value = local,
        onValueChange = { local = it },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        startLabel = label,
        endLabel = format(shown),
        index = index,
        count = count
    )
}

private const val COMMIT_DELAY_MILLIS = 600L
