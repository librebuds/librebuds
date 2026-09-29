// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.model

import androidx.annotation.StringRes
import io.github.librebuds.R
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.command.HostAction
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.session.GESTURE_SUB_KEYS
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.SettingChange
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Which string family an option's semantic key belongs to. */
enum class OptionGroup { GESTURE, NOISE_CYCLE, EQUALIZER, SOUND_QUALITY }

/** One selectable device value. [key] is the profile's semantic name, or null when the profile does not name it. */
data class SettingOption(val code: Int, val key: String?)

/** A picker: the current device value (null = not reported) and what can be chosen. */
data class Picker(val current: Int?, val options: List<SettingOption>, val group: OptionGroup)

/** One gesture's pickers; a side is null when that value is not shown (not reported, or swipe's mirrored right side). */
data class GestureControl(
    val subKey: String,
    val gesture: Gesture,
    val left: Picker?,
    val right: Picker?,
    val inCall: Picker?,
)

/**
 * The settings controls to show for one profile and state. A control is present only when the
 * profile lists its capability, the device reported a value and its read did not go unanswered.
 * [experimental] holds the listed capability keys that have no `verified` date.
 */
data class SettingsModel(
    val wear: Boolean? = null,
    val gestures: List<GestureControl> = emptyList(),
    val equalizer: Picker? = null,
    val lowLatency: Boolean? = null,
    val soundQuality: Picker? = null,
    /** Present (possibly with a null current language) when the language was read. */
    val language: LanguageRow? = null,
    val multipointEnabled: Boolean? = null,
    val experimental: Set<String> = emptySet(),
) {
    val hasSound: Boolean get() = lowLatency != null || soundQuality != null || language != null
}

data class LanguageRow(val current: String?)

fun settingsModel(profile: Profile, state: BudsState): SettingsModel {
    val settings = state.settings
    fun shown(key: String, capability: String = key) = profile.supports(capability) && key !in settings.unanswered
    return SettingsModel(
        wear = settings.wearDetection?.takeIf { shown("wear") },
        gestures = gestureControls(profile, state),
        equalizer = settings.equalizer?.takeIf { shown("equalizer") }?.let { eq ->
            picker(eq.active, options(profile.capabilities["equalizer"]?.table("presets"), eq.available), OptionGroup.EQUALIZER)
        },
        lowLatency = settings.lowLatency?.takeIf { shown("lowLatency") },
        soundQuality = settings.soundQuality?.takeIf { shown("soundQuality") }?.let {
            picker(it, options(profile.capabilities["soundQuality"]?.table("options"), emptyList()), OptionGroup.SOUND_QUALITY)
        },
        language = settings.language?.takeIf { shown("language") }?.let { LanguageRow(it.current) },
        multipointEnabled = state.multipointEnabled?.takeIf { shown("multipoint") },
        experimental = profile.capabilities.keys.filterTo(mutableSetOf()) { profile.verifiedOn(it) == null },
    )
}

private fun gestureControls(profile: Profile, state: BudsState): List<GestureControl> {
    val listed = profile.capabilities["gestures"] ?: return emptyList()
    return GESTURE_SUB_KEYS.mapNotNull { (subKey, gesture) ->
        val entry = listed[subKey] as? JsonObject ?: return@mapNotNull null
        if ("gestures.$subKey" in state.settings.unanswered) return@mapNotNull null
        val setting = state.settings.gestures[gesture] ?: return@mapNotNull null
        val group = if (gesture == Gesture.NOISE_CYCLE) OptionGroup.NOISE_CYCLE else OptionGroup.GESTURE
        val options = options(entry.table("options"), setting.supported)
        val withInCall = (entry["inCall"] as? JsonPrimitive)?.booleanOrNull == true
        GestureControl(
            subKey = subKey,
            gesture = gesture,
            left = setting.left?.let { picker(it, options, group) },
            right = setting.right?.takeIf { gesture != Gesture.SWIPE }?.let { picker(it, options, group) },
            inCall = setting.inCall?.takeIf { withInCall }?.let {
                picker(it, options(entry.table("inCallOptions"), emptyList()), OptionGroup.GESTURE)
            },
        ).takeIf { it.left != null || it.right != null || it.inCall != null }
    }
}

/** A picker with nothing to choose from is not shown at all. */
private fun picker(current: Int?, options: List<SettingOption>, group: OptionGroup): Picker? =
    Picker(current, options, group).takeIf { options.isNotEmpty() }

private fun JsonObject.table(name: String): JsonObject? = this[name] as? JsonObject

/** The profile's code -> semantic key table in its order; without one, the device's own codes unnamed. */
private fun options(table: JsonObject?, fallback: List<Int>): List<SettingOption> {
    val named = table?.mapNotNull { (code, key) ->
        val value = (key as? JsonPrimitive)?.takeIf { it.isString }?.content
        code.toIntOrNull()?.let { SettingOption(it, value) }
    }.orEmpty()
    return named.ifEmpty { fallback.map { SettingOption(it, null) } }
}

/** The semantic name the picker's options give [code], or null when none does. */
fun Picker.keyOf(code: Int): String? = options.firstOrNull { it.code == code }?.key

/** The string for a semantic option key, or null for a key this app does not know (show the raw code). */
@StringRes
fun optionLabelRes(group: OptionGroup, key: String?): Int? = when (group) {
    OptionGroup.GESTURE -> when (key) {
        "off" -> R.string.gesture_off
        "pause" -> R.string.gesture_pause
        "next" -> R.string.gesture_next
        "previous" -> R.string.gesture_previous
        "assistant" -> R.string.gesture_assistant
        "switch_anc" -> R.string.gesture_switch_anc
        "answer" -> R.string.gesture_answer
        "volume" -> R.string.gesture_volume
        else -> null
    }
    OptionGroup.NOISE_CYCLE -> when (key) {
        "off_on" -> R.string.noise_cycle_off_on
        "off_on_awareness" -> R.string.noise_cycle_off_on_awareness
        "on_awareness" -> R.string.noise_cycle_on_awareness
        "off_awareness" -> R.string.noise_cycle_off_awareness
        else -> null
    }
    OptionGroup.EQUALIZER -> when (key) {
        "default" -> R.string.eq_default
        "bass" -> R.string.eq_bass
        "treble" -> R.string.eq_treble
        "voice" -> R.string.eq_voice
        else -> null
    }
    OptionGroup.SOUND_QUALITY -> when (key) {
        "connectivity" -> R.string.sound_connectivity
        "quality" -> R.string.sound_quality
        else -> null
    }
}

/**
 * The control a change belongs to; the view model keeps one pending change per key. A gesture's
 * sides are separate controls, as are the host commands for each host.
 */
fun SettingChange.controlKey(): String = when (this) {
    is SettingChange.Wear -> "wear"
    is SettingChange.GestureChange -> {
        val sides = listOfNotNull(left?.let { "left" }, right?.let { "right" }, inCall?.let { "inCall" })
        "gesture.${gesture.name}.${sides.joinToString("+")}"
    }
    is SettingChange.EqualizerPreset -> "equalizer"
    is SettingChange.LowLatencyChange -> "lowLatency"
    is SettingChange.SoundQualityChange -> "soundQuality"
    is SettingChange.MultipointEnabled -> "multipoint"
    // Choosing one preferred host changes the others too, so all preferred-host changes share a key.
    is SettingChange.PreferredHost -> "preferredHost"
    is SettingChange.HostCommand -> "host.${mac.uppercase()}"
}

/** [state] as it will look once the device accepted this change; the optimistic value the UI shows. */
fun SettingChange.applyTo(state: BudsState): BudsState {
    val settings = state.settings
    return when (this) {
        is SettingChange.Wear -> state.copy(settings = settings.copy(wearDetection = enabled))
        is SettingChange.GestureChange -> {
            val current = settings.gestures[gesture] ?: return state
            val next = current.copy(
                left = left ?: current.left,
                right = if (gesture == Gesture.SWIPE) current.right else right ?: current.right,
                inCall = inCall ?: current.inCall,
            )
            state.copy(settings = settings.copy(gestures = settings.gestures + (gesture to next)))
        }
        is SettingChange.EqualizerPreset -> settings.equalizer?.let {
            state.copy(settings = settings.copy(equalizer = it.copy(active = preset)))
        } ?: state
        is SettingChange.LowLatencyChange -> state.copy(settings = settings.copy(lowLatency = enabled))
        is SettingChange.SoundQualityChange -> state.copy(settings = settings.copy(soundQuality = value))
        is SettingChange.MultipointEnabled -> state.copy(multipointEnabled = enabled)
        is SettingChange.PreferredHost -> state.copy(hosts = state.hosts.map { it.copy(preferred = it.mac.equals(mac, ignoreCase = true)) })
        is SettingChange.HostCommand -> state.copy(
            hosts = state.hosts.map { host ->
                if (!host.mac.equals(mac, ignoreCase = true)) return@map host
                when (action) {
                    HostAction.CONNECT -> if (host.connected) host else host.copy(connection = 1)
                    HostAction.DISCONNECT -> host.copy(connection = 0)
                    HostAction.ENABLE_AUTO_CONNECT -> host.copy(autoConnect = true)
                    HostAction.DISABLE_AUTO_CONNECT -> host.copy(autoConnect = false)
                }
            },
        )
    }
}
