// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import io.github.librebuds.protocol.command.EqualizerState
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.command.GestureSetting
import io.github.librebuds.protocol.command.HostAction
import io.github.librebuds.protocol.command.LanguageInfo

/** Per-model settings as last read from the device. Null (or empty) means not supported or not reported yet. */
data class DeviceSettings(
    val wearDetection: Boolean? = null,
    val gestures: Map<Gesture, GestureSetting> = emptyMap(),
    val equalizer: EqualizerState? = null,
    val lowLatency: Boolean? = null,
    val soundQuality: Int? = null,
    val language: LanguageInfo? = null,
)

/** One requested settings change; the repository confirms it by reading the setting back. */
sealed interface SettingChange {
    data class Wear(val enabled: Boolean) : SettingChange

    /** Null fields are left unchanged. For [Gesture.SWIPE] only [left] is used (the device mirrors it). */
    data class GestureChange(val gesture: Gesture, val left: Int?, val right: Int?, val inCall: Int?) : SettingChange

    data class EqualizerPreset(val preset: Int) : SettingChange

    data class LowLatencyChange(val enabled: Boolean) : SettingChange

    data class SoundQualityChange(val value: Int) : SettingChange

    data class MultipointEnabled(val enabled: Boolean) : SettingChange

    data class PreferredHost(val mac: String) : SettingChange

    data class HostCommand(val action: HostAction, val mac: String) : SettingChange
}
