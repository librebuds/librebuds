// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.model

import androidx.annotation.StringRes
import io.github.librebuds.R
import io.github.librebuds.protocol.command.Anc
import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.CustomPreset
import io.github.librebuds.protocol.command.EqOperation
import io.github.librebuds.protocol.command.Equalizer
import io.github.librebuds.protocol.command.Feature
import io.github.librebuds.protocol.command.FeatureState
import io.github.librebuds.protocol.command.FeatureSwitch
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.command.HostAction
import io.github.librebuds.protocol.command.PinchSetting
import io.github.librebuds.protocol.command.PinchSlot
import io.github.librebuds.protocol.command.Side
import io.github.librebuds.protocol.profile.FEATURE_CAPABILITIES
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.ancModes
import io.github.librebuds.protocol.profile.awarenessModes
import io.github.librebuds.protocol.profile.customEqualizer
import io.github.librebuds.protocol.profile.extendedPresets
import io.github.librebuds.protocol.profile.features
import io.github.librebuds.protocol.profile.hasAwarenessLevel
import io.github.librebuds.protocol.profile.headActions
import io.github.librebuds.protocol.profile.pinchSlots
import io.github.librebuds.session.GESTURE_SUB_KEYS
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.SettingChange
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Which string family an option's semantic key belongs to. */
enum class OptionGroup { GESTURE, NOISE_CYCLE, EQUALIZER, SOUND_QUALITY, CANCELLATION_LEVEL, AWARENESS, EAR_TIP, PICKUP }

/**
 * One selectable device value. [key] is the profile's semantic name, or null when the profile does
 * not name it; [label] is a literal name the earbuds report (a custom equalizer preset's name).
 */
data class SettingOption(val code: Int, val key: String?, val label: String? = null)

/**
 * A picker: the current device value (null = not reported) and what can be chosen. [labels] name
 * values that are not offered (a current value outside [options]) without offering them.
 */
data class Picker(
    val current: Int?,
    val options: List<SettingOption>,
    val group: OptionGroup,
    val labels: List<SettingOption> = emptyList(),
)

/** One gesture's pickers; a side is null when that value is not shown (not reported, or swipe's mirrored right side). */
data class GestureControl(
    val subKey: String,
    val gesture: Gesture,
    val left: Picker?,
    val right: Picker?,
    val inCall: Picker?,
)

/** One pinch slot's pickers: [left] carries the single value of a tap slot; [right] only for pinch and hold. */
data class PinchControl(val slot: PinchSlot, val left: Picker?, val right: Picker?)

/** Head control: on/off and, while on, the nod and shake actions. */
data class HeadControl(val enabled: Boolean, val nod: Picker?, val shake: Picker?)

/** Custom equalizer presets: the ones saved, and the slot a new one would take (null when all three are used). */
data class CustomEqualizerModel(val presets: List<CustomPreset>, val freeSlot: Int?)

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
    /** The low-latency row is the dynamic-latency variant on this model. */
    val dynamicLatency: Boolean = false,
    val soundQuality: Picker? = null,
    /** Present (possibly with a null current language) when the language was read. */
    val language: LanguageRow? = null,
    val multipointEnabled: Boolean? = null,
    val experimental: Set<String> = emptySet(),
    /** Awareness sub-mode, shown while awareness is on. */
    val awareness: Picker? = null,
    /** The adaptive awareness slider (0..10), shown while the adaptive sub-mode is on. */
    val awarenessLevel: Int? = null,
    /** On/off feature switches the earbuds offer (single-bud noise cancelling, adaptive volume, ...). */
    val switches: Map<Feature, Boolean> = emptyMap(),
    val headControl: HeadControl? = null,
    val earTip: Picker? = null,
    val pinch: List<PinchControl> = emptyList(),
    val customEqualizer: CustomEqualizerModel? = null,
    val restReminder: Boolean? = null,
    val hdCall: Boolean? = null,
    val pickupMode: Picker? = null,
    /** Each side's sound state while the earbuds answer find requests; null when not offered. */
    val findEarbuds: Map<Side, Boolean>? = null,
) {
    val hasSound: Boolean get() = lowLatency != null || soundQuality != null || language != null
}

data class LanguageRow(val current: String?)

fun settingsModel(profile: Profile, state: BudsState): SettingsModel {
    val settings = state.settings
    fun shown(key: String, capability: String = key) =
        profile.supports(capability) && key !in settings.unanswered && key !in settings.unsupported
    return SettingsModel(
        wear = settings.wearDetection?.takeIf { shown("wear") },
        gestures = gestureControls(profile, state),
        equalizer = settings.equalizer?.takeIf { shown("equalizer") }?.let { eq ->
            val names = options(profile.capabilities["equalizer"]?.table("presets"), emptyList()).associate { it.code to it.key }
            // Extended presets once the earbuds said they have them; custom presets by their own names.
            val extended = profile.extendedPresets().keys.takeIf { settings.equalizerExtended == true }.orEmpty()
                .filter { id -> eq.available.none { it == id } }.map { SettingOption(it, names[it]) }
            val custom = eq.custom.takeIf { profile.customEqualizer() }.orEmpty().map { SettingOption(it.id, null, it.name.ifEmpty { null }) }
            picker(eq.active, equalizerOptions(profile.capabilities["equalizer"], eq.available) + extended + custom, OptionGroup.EQUALIZER)
        },
        lowLatency = settings.lowLatency?.takeIf { shown("lowLatency") },
        dynamicLatency = settings.lowLatencyDynamic == true,
        soundQuality = settings.soundQuality?.takeIf { shown("soundQuality") }?.let {
            picker(it, options(profile.capabilities["soundQuality"]?.table("options"), emptyList()), OptionGroup.SOUND_QUALITY)
        },
        language = settings.language?.takeIf { shown("language") }?.let { LanguageRow(it.current) },
        multipointEnabled = state.multipointEnabled?.takeIf { shown("multipoint") },
        experimental = profile.capabilities.keys.filterTo(mutableSetOf()) { profile.verifiedOn(it) == null },
        awareness = awarenessPicker(profile, state),
        awarenessLevel = state.anc?.takeIf { profile.hasAwarenessLevel() && it.mode == AncMode.AWARENESS && it.level == Anc.AWARENESS_ADAPTIVE }?.awarenessLevel,
        switches = profile.features().filter { it in SWITCH_FEATURES }.mapNotNull { feature ->
            featureState(feature, state)?.let { feature to (it == 1) }
        }.toMap(),
        headControl = headControl(profile, state),
        earTip = featureState(Feature.EAR_TIP, state)?.takeIf { Feature.EAR_TIP in profile.features() }?.let {
            picker(it, listOf(SettingOption(EAR_TIP_SILICONE, "silicone"), SettingOption(EAR_TIP_FOAM, "foam")), OptionGroup.EAR_TIP)
        },
        pinch = pinchControls(profile, state),
        customEqualizer = settings.equalizer?.takeIf { shown("equalizer") && profile.customEqualizer() }?.let {
            CustomEqualizerModel(it.custom, Equalizer.freeSlot(it.custom))
        },
        restReminder = settings.restReminder?.takeIf { profile.supports("restReminder") },
        hdCall = settings.hdCall?.takeIf { profile.supports("hdCall") },
        pickupMode = settings.pickupMode?.takeIf { profile.supports("pickupMode") }?.let {
            picker(it, listOf(SettingOption(PICKUP_VOICES, "voices"), SettingOption(PICKUP_SURROUNDINGS, "surroundings")), OptionGroup.PICKUP)
        },
        findEarbuds = settings.ringing.takeIf { profile.supports("findEarbuds") && it.isNotEmpty() },
    )
}

/** Feature switches shown as plain on/off rows. */
private val SWITCH_FEATURES = setOf(Feature.SINGLE_BUD_ANC, Feature.ADAPTIVE_VOLUME, Feature.AI_CONVERSATION, Feature.DROP_DETECTION)

private const val EAR_TIP_SILICONE = 1
private const val EAR_TIP_FOAM = 2
private const val PICKUP_VOICES = 1
private const val PICKUP_SURROUNDINGS = 0

/**
 * A feature switch's state when the earbuds offer it: listed in the ability answer (features that
 * have an entry there) and with a state from its read or, until that arrives, the ability answer.
 */
private fun featureState(feature: Feature, state: BudsState): Int? {
    val settings = state.settings
    val read = settings.features[feature]?.state
    val capability = feature.capability ?: return read
    val offered = settings.abilities?.capabilities?.get(capability) ?: return null
    return read ?: offered
}

private fun headControl(profile: Profile, state: BudsState): HeadControl? {
    if (Feature.HEAD_CONTROL !in profile.features()) return null
    val on = featureState(Feature.HEAD_CONTROL, state) ?: return null
    val read: FeatureState? = state.settings.features[Feature.HEAD_CONTROL]
    val actions = profile.headActions().map { SettingOption(it.code, it.key) }
    return HeadControl(
        enabled = on == 1,
        nod = read?.first?.let { picker(it, actions, OptionGroup.GESTURE) },
        shake = read?.second?.let { picker(it, actions, OptionGroup.GESTURE) },
    )
}

private fun awarenessPicker(profile: Profile, state: BudsState): Picker? {
    val anc = state.anc?.takeIf { it.mode == AncMode.AWARENESS } ?: return null
    val modes = profile.awarenessModes().takeIf { it.size >= 2 } ?: return null
    return picker(anc.level, modes.map { SettingOption(it, AWARENESS_KEYS[it]) }, OptionGroup.AWARENESS)
}

private val AWARENESS_KEYS = mapOf(Anc.AWARENESS_STANDARD to "standard", Anc.AWARENESS_VOICE to "voice", Anc.AWARENESS_ADAPTIVE to "adaptive")

private fun pinchControls(profile: Profile, state: BudsState): List<PinchControl> =
    profile.pinchSlots().mapNotNull { spec ->
        val setting = state.settings.pinch[spec.slot] ?: return@mapNotNull null
        val options = spec.options.map { SettingOption(it.code, it.key) }
        val hold = spec.slot.type == 3
        PinchControl(
            slot = spec.slot,
            left = setting.left?.let { picker(it, options, OptionGroup.GESTURE) },
            right = setting.right?.takeIf { hold }?.let { picker(it, options, OptionGroup.GESTURE) },
        ).takeIf { it.left != null || it.right != null }
    }

private fun gestureControls(profile: Profile, state: BudsState): List<GestureControl> {
    val listed = profile.capabilities["gestures"] ?: return emptyList()
    val gestures = state.settings.gestures
    return GESTURE_SUB_KEYS.mapNotNull { (subKey, gesture) ->
        val entry = listed[subKey] as? JsonObject ?: return@mapNotNull null
        if ("gestures.$subKey" in state.settings.unanswered) return@mapNotNull null
        val setting = gestures[gesture] ?: return@mapNotNull null
        // Like the vendor app, the noise-control cycle only matters while press and hold switches
        // noise control on at least one earbud.
        if (gesture == Gesture.NOISE_CYCLE && listed["longPress"] != null) {
            val press = gestures[Gesture.LONG_PRESS]
            if (press != null && press.left != SWITCH_NOISE_CONTROL && press.right != SWITCH_NOISE_CONTROL) return@mapNotNull null
        }
        val group = if (gesture == Gesture.NOISE_CYCLE) OptionGroup.NOISE_CYCLE else OptionGroup.GESTURE
        val all = accepted(options(entry.table("options"), setting.supported), setting.supported)
        // Never offer a cycle through a noise-control mode the profile does not list.
        val offered = if (gesture == Gesture.NOISE_CYCLE) {
            val modes = profile.ancModes().toSet()
            all.filter { option -> noiseCycleModes(option.key)?.let { modes.containsAll(it) } ?: true }
        } else {
            all
        }
        // Values the vendor app does not offer are only named, for when one is the current value.
        val hidden = all - offered.toSet() + options(entry.table("hiddenOptions"), emptyList())
        val flag = { name: String -> (entry[name] as? JsonPrimitive)?.booleanOrNull == true }
        val single = gesture == Gesture.SWIPE || flag("bothSides")
        GestureControl(
            subKey = subKey,
            gesture = gesture,
            left = setting.left?.let { picker(it, offered, group, hidden) },
            right = setting.right?.takeIf { !single }?.let { picker(it, offered, group, hidden) },
            inCall = setting.inCall?.takeIf { flag("inCall") }?.let {
                picker(it, accepted(options(entry.table("inCallOptions"), emptyList()), setting.inCallSupported), OptionGroup.GESTURE, hidden)
            },
        ).takeIf { it.left != null || it.right != null || it.inCall != null }
    }
}

/** Press-and-hold code that switches noise control (and so uses the noise-control cycle). */
private const val SWITCH_NOISE_CONTROL = 10

/**
 * [options] limited to the codes the earbuds report they accept, when they report any; a list
 * that shares nothing with the profile is taken as misread and ignored rather than blanking the row.
 */
private fun accepted(options: List<SettingOption>, reported: List<Int>): List<SettingOption> {
    if (reported.isEmpty()) return options
    return options.filter { it.code in reported }.ifEmpty { options }
}

/**
 * The presets the earbuds report (in their order), named from the profile's table; without a
 * reported list, the profile's `offered` ids; with neither, nothing (the picker is not shown).
 */
private fun equalizerOptions(capability: JsonObject?, available: List<Int>): List<SettingOption> {
    val names = options(capability?.table("presets"), emptyList()).associate { it.code to it.key }
    val offered = (capability?.get("offered") as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }.orEmpty()
    return available.ifEmpty { offered }.distinct().map { SettingOption(it, names[it]) }
}

/** A picker with nothing to choose from is not shown at all. */
private fun picker(current: Int?, options: List<SettingOption>, group: OptionGroup, labels: List<SettingOption> = emptyList()): Picker? =
    Picker(current, options, group, labels.filter { it.code == current }).takeIf { options.isNotEmpty() }

private fun JsonObject.table(name: String): JsonObject? = this[name] as? JsonObject

/** The profile's code -> semantic key table in its order; without one, the device's own codes unnamed. */
private fun options(table: JsonObject?, fallback: List<Int>): List<SettingOption> {
    val named = table?.mapNotNull { (code, key) ->
        val value = (key as? JsonPrimitive)?.takeIf { it.isString }?.content
        code.toIntOrNull()?.let { SettingOption(it, value) }
    }.orEmpty()
    return named.ifEmpty { fallback.map { SettingOption(it, null) } }
}

/** The literal name the picker's options give [code] (a custom preset's), or null. */
fun Picker.labelOf(code: Int): String? = (options.firstOrNull { it.code == code } ?: labels.firstOrNull { it.code == code })?.label

/** The semantic name the picker's options give [code], or null when none does. */
fun Picker.keyOf(code: Int): String? = (options.firstOrNull { it.code == code } ?: labels.firstOrNull { it.code == code })?.key

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
        "reject" -> R.string.gesture_reject
        "toggle_anc" -> R.string.gesture_toggle_anc
        "quick_play" -> R.string.gesture_quick_play
        "song_id" -> R.string.gesture_song_id
        "record" -> R.string.gesture_record
        "volume" -> R.string.gesture_volume
        "answer_end" -> R.string.gesture_answer_end
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
        "lively" -> R.string.eq_lively
        "balanced" -> R.string.eq_balanced
        "classical" -> R.string.eq_classical
        "movie" -> R.string.eq_movie
        "gaming" -> R.string.eq_gaming
        "podcast" -> R.string.eq_podcast
        "punchy" -> R.string.eq_punchy
        "adaptive" -> R.string.eq_adaptive
        "natural" -> R.string.eq_natural
        "concert" -> R.string.eq_concert
        "shooter" -> R.string.eq_shooter
        "symphony" -> R.string.eq_symphony
        "hifi_live" -> R.string.eq_hifi_live
        "sound_bass" -> R.string.eq_sound_bass
        "sound_balanced" -> R.string.eq_sound_balanced
        "sound_voice" -> R.string.eq_sound_voice
        "sound_classical" -> R.string.eq_sound_classical
        else -> null
    }
    OptionGroup.SOUND_QUALITY -> when (key) {
        "connectivity" -> R.string.sound_connectivity
        "quality" -> R.string.sound_quality
        else -> null
    }
    OptionGroup.CANCELLATION_LEVEL -> when (key) {
        "dynamic" -> R.string.cancellation_level_dynamic
        "cozy" -> R.string.cancellation_level_cozy
        "general" -> R.string.cancellation_level_general
        "ultra" -> R.string.cancellation_level_ultra
        "dual_engine" -> R.string.cancellation_level_dual_engine
        else -> null
    }
    OptionGroup.AWARENESS -> when (key) {
        "standard" -> R.string.awareness_standard
        "voice" -> R.string.awareness_voice
        "adaptive" -> R.string.awareness_adaptive
        else -> null
    }
    OptionGroup.EAR_TIP -> when (key) {
        "silicone" -> R.string.ear_tip_silicone
        "foam" -> R.string.ear_tip_foam
        else -> null
    }
    OptionGroup.PICKUP -> when (key) {
        "voices" -> R.string.pickup_voices
        "surroundings" -> R.string.pickup_surroundings
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
    is SettingChange.FeatureValue -> "feature.${feature.name}.$field"
    is SettingChange.PinchChange -> "pinch.$slot.${listOfNotNull(left?.let { "left" }, right?.let { "right" }).joinToString("+")}"
    is SettingChange.AwarenessMode -> "awarenessMode"
    is SettingChange.AwarenessLevel -> "awarenessLevel"
    is SettingChange.CustomEqualizer -> "customEqualizer.$id"
    is SettingChange.RestReminderChange -> "restReminder"
    is SettingChange.HdCallChange -> "hdCall"
    is SettingChange.PickupModeChange -> "pickupMode"
}

/** [state] as it will look once the device accepted this change; the optimistic value the UI shows. */
fun SettingChange.applyTo(state: BudsState): BudsState {
    val settings = state.settings
    return when (this) {
        is SettingChange.Wear -> state.copy(settings = settings.copy(wearDetection = enabled))
        is SettingChange.GestureChange -> {
            val current = settings.gestures[gesture] ?: return state
            // A single-value gesture (swipe, bothSides) only ever changes left; right is not shown.
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
        is SettingChange.FeatureValue -> {
            val old = settings.features[feature] ?: FeatureState(feature.key, null)
            val next = when (field) {
                2 -> old.copy(state = value)
                FeatureSwitch.NOD -> old.copy(first = value)
                else -> old.copy(second = value)
            }
            state.copy(settings = settings.copy(features = settings.features + (feature to next)))
        }
        is SettingChange.PinchChange -> {
            val old = settings.pinch[slot] ?: return state
            val next = if (slot.type == 3) {
                old.copy(left = left ?: old.left, right = right ?: old.right)
            } else {
                PinchSetting(slot, left ?: old.left, left ?: old.right)
            }
            state.copy(settings = settings.copy(pinch = settings.pinch + (slot to next)))
        }
        is SettingChange.AwarenessMode -> state.anc?.let { state.copy(anc = it.copy(modeCode = AncMode.AWARENESS.code, level = subMode)) } ?: state
        is SettingChange.AwarenessLevel -> state.anc?.let { state.copy(anc = it.copy(awarenessLevel = level)) } ?: state
        is SettingChange.CustomEqualizer -> settings.equalizer?.let { eq ->
            val others = eq.custom.filter { it.id != id }
            when (operation) {
                EqOperation.PREVIEW -> state
                EqOperation.SAVE -> state.copy(settings = settings.copy(equalizer = eq.copy(active = id, custom = (others + CustomPreset(id, gains, name)).sortedBy { it.id })))
                EqOperation.DELETE -> state.copy(settings = settings.copy(equalizer = eq.copy(custom = others)))
            }
        } ?: state
        is SettingChange.RestReminderChange -> state.copy(settings = settings.copy(restReminder = enabled))
        is SettingChange.HdCallChange -> state.copy(settings = settings.copy(hdCall = enabled))
        is SettingChange.PickupModeChange -> state.copy(settings = settings.copy(pickupMode = mode))
    }
}

/** The profile capability key a feature switch is listed under. */
fun Feature.capabilityKey(): String = FEATURE_CAPABILITIES.getValue(this)
