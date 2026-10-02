// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import io.github.librebuds.protocol.command.EqOperation
import io.github.librebuds.protocol.command.EqualizerState
import io.github.librebuds.protocol.command.Feature
import io.github.librebuds.protocol.command.FeatureAbilities
import io.github.librebuds.protocol.command.FeatureState
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.command.GestureSetting
import io.github.librebuds.protocol.command.HostAction
import io.github.librebuds.protocol.command.LanguageInfo
import io.github.librebuds.protocol.command.PinchSetting
import io.github.librebuds.protocol.command.PinchSlot
import io.github.librebuds.protocol.command.Side

/**
 * Per-model settings as last read from the device. Null (or empty) means not supported or not reported yet.
 * [unanswered] holds the capability keys whose read timed out this session (`wear`, `equalizer`,
 * `gestures.doubleTap`, ...); the UI hides those and the repository refuses changes to them.
 * [unsupported] holds the keys the earbuds said they do not offer (a low-latency probe without
 * TLV 3, a sound-quality capability below 1); they are hidden and refused the same way, but a
 * later value does not bring them back. [lowLatencyDynamic] is the probe's variant (true = dynamic latency).
 *
 * [abilities] is the feature-switch ability answer (null: not answered); [features] the feature
 * switch states read so far; [pinch] the pinch slots read so far; [equalizerExtended] whether the
 * extended presets are available; [ringing] each side's find-earbuds sound state, present once the
 * earbuds answered a state query (or reported one).
 */
data class DeviceSettings(
    val wearDetection: Boolean? = null,
    val gestures: Map<Gesture, GestureSetting> = emptyMap(),
    val equalizer: EqualizerState? = null,
    val lowLatency: Boolean? = null,
    val lowLatencyDynamic: Boolean? = null,
    val soundQuality: Int? = null,
    val language: LanguageInfo? = null,
    val abilities: FeatureAbilities? = null,
    val features: Map<Feature, FeatureState> = emptyMap(),
    val pinch: Map<PinchSlot, PinchSetting> = emptyMap(),
    val equalizerExtended: Boolean? = null,
    val restReminder: Boolean? = null,
    val hdCall: Boolean? = null,
    val pickupMode: Int? = null,
    val ringing: Map<Side, Boolean> = emptyMap(),
    val unanswered: Set<String> = emptySet(),
    val unsupported: Set<String> = emptySet(),
)

/** One requested settings change; the repository confirms it by reading the setting back. */
sealed interface SettingChange {
    data class Wear(val enabled: Boolean) : SettingChange

    /**
     * Null fields are left unchanged. For a gesture with one value for both earbuds (swipe, and any
     * the profile marks `bothSides`) only [left] is used.
     */
    data class GestureChange(val gesture: Gesture, val left: Int?, val right: Int?, val inCall: Int?) : SettingChange

    data class EqualizerPreset(val preset: Int) : SettingChange

    data class LowLatencyChange(val enabled: Boolean) : SettingChange

    data class SoundQualityChange(val value: Int) : SettingChange

    data class MultipointEnabled(val enabled: Boolean) : SettingChange

    data class PreferredHost(val mac: String) : SettingChange

    data class HostCommand(val action: HostAction, val mac: String) : SettingChange

    /** A feature switch field: TLV 2 (on/off, or the ear tip type) or head control's nod or shake action. */
    data class FeatureValue(val feature: Feature, val value: Int, val field: Int = 2) : SettingChange

    /** A pinch slot; tap slots send one value for both earbuds ([left] only), pinch and hold one side per change. */
    data class PinchChange(val slot: PinchSlot, val left: Int?, val right: Int?) : SettingChange

    /** Awareness sub-mode (sent as the awareness level byte). */
    data class AwarenessMode(val subMode: Int) : SettingChange

    /** The adaptive awareness slider, 0..10. */
    data class AwarenessLevel(val level: Int) : SettingChange

    /** A custom equalizer preset: try, save or delete slot [id]. */
    data class CustomEqualizer(val id: Int, val gains: List<Int>, val name: String, val operation: EqOperation) : SettingChange

    data class RestReminderChange(val enabled: Boolean) : SettingChange

    data class HdCallChange(val enabled: Boolean) : SettingChange

    data class PickupModeChange(val mode: Int) : SettingChange
}
