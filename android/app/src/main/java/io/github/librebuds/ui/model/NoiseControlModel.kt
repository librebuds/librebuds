// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.model

import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.ancModes
import io.github.librebuds.protocol.profile.cancellationLevelKey
import io.github.librebuds.protocol.profile.cancellationLevels

/**
 * The noise-control modes to offer for [profile], in display order: only the modes the profile
 * lists. Off is left out when [showOff] is false, unless that would leave fewer than two modes
 * (a model with only off and cancellation could otherwise never turn cancellation off).
 */
fun offeredModes(profile: Profile, showOff: Boolean): List<NoiseControlMode> = offeredModes(listedModes(profile), showOff)

/** [offeredModes] for modes already resolved from a profile with [listedModes]. */
fun offeredModes(listed: List<NoiseControlMode>, showOff: Boolean): List<NoiseControlMode> {
    val withoutOff = listed.filter { it != NoiseControlMode.OFF }
    return if (showOff || withoutOff.size < 2) listed else withoutOff
}

/** Every noise-control mode [profile] lists, in display order. */
fun listedModes(profile: Profile): List<NoiseControlMode> = profile.ancModes().mapNotNull(::noiseControlMode)

/** The mode with protocol [code] when [profile] lists it, else null (a stale widget button, say). */
fun listedMode(code: Int, profile: Profile): AncMode? = AncMode.of(code)?.takeIf { mode -> listedModes(profile).any { it.anc == mode } }

private fun noiseControlMode(mode: AncMode): NoiseControlMode? = NoiseControlMode.entries.firstOrNull { it.anc == mode }

/**
 * The cancellation level picker for [profile]: the device's [currentLevel] (null when not
 * reported) and the profile's levels in its order. Null when the profile lists no levels.
 * The current level is named by its shared meaning even when the profile does not offer it, so
 * a level the earbuds picked themselves still reads as e.g. "Ultra" rather than a raw code.
 */
fun cancellationLevelPicker(profile: Profile, currentLevel: Int?): Picker? {
    val levels = profile.cancellationLevels()
    if (levels.isEmpty()) return null
    val options = levels.map { SettingOption(it, profile.cancellationLevelKey(it)) }
    val extra = currentLevel?.takeIf { level -> options.none { it.code == level } }
        ?.let { SettingOption(it, profile.cancellationLevelKey(it)) }
    return Picker(currentLevel, options, OptionGroup.CANCELLATION_LEVEL, labels = listOfNotNull(extra))
}

/**
 * The modes a noise-cycle option key cycles through (`off_on_awareness` = off, cancellation and
 * awareness; "on" is cancellation), or null for a key this app does not know.
 */
internal fun noiseCycleModes(key: String?): Set<AncMode>? {
    val parts = key?.split('_')?.takeIf { it.isNotEmpty() } ?: return null
    return parts.map { part ->
        when (part) {
            "off" -> AncMode.OFF
            "on" -> AncMode.CANCELLATION
            "awareness" -> AncMode.AWARENESS
            else -> return null
        }
    }.toSet()
}
