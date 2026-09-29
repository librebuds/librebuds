// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import io.github.librebuds.protocol.beacon.Beacon
import io.github.librebuds.protocol.profile.ProfileRegistry

data class BeaconSighting(val address: String, val rssi: Int, val beacon: Beacon, val atMillis: Long)

enum class PopupDecision { SHOW, IGNORE_NOT_CLOSE_RANGE, IGNORE_UNKNOWN, IGNORE_FAR, IGNORE_COOLDOWN }

/** Whether a case-open beacon should raise the popup (spec 6.1). */
class PopupRules(
    private val cooldownMillis: Long = 30_000,
    private val margin: Int = 10,
    private val defaultReference: Int = -60,
) {
    fun decide(sighting: BeaconSighting, known: Boolean, lastShownAt: Long?): PopupDecision {
        val beacon = sighting.beacon
        if (!beacon.closeRangeEnabled) return PopupDecision.IGNORE_NOT_CLOSE_RANGE
        if (!known) return PopupDecision.IGNORE_UNKNOWN
        // SPEC-GAP: proximity formula (reference RSSI minus a margin) awaits test round 3.
        val threshold = (beacon.referenceRssi ?: defaultReference) - margin
        if (sighting.rssi < threshold) return PopupDecision.IGNORE_FAR
        // A time after now means the wall clock was set back: the cooldown no longer applies.
        if (lastShownAt != null && sighting.atMillis - lastShownAt in 0 until cooldownMillis) return PopupDecision.IGNORE_COOLDOWN
        return PopupDecision.SHOW
    }
}

fun isKnown(beacon: Beacon, registry: ProfileRegistry, associatedModelIds: Set<String>): Boolean {
    val modelId = beacon.modelId ?: return false
    if (associatedModelIds.any { it.equals(modelId, ignoreCase = true) }) return true
    return registry.match(modelId = modelId).id != ProfileRegistry.GENERIC.id
}
