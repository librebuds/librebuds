// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import io.github.librebuds.protocol.beacon.Beacon
import io.github.librebuds.protocol.profile.Profile
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

/**
 * The profile a case-open beacon belongs to, or null when it is not one of ours. Looks up the beacon's
 * modelId, then its newModelId, in the registry; then the [associated] profile (the earbuds added in the
 * app) when it lists either id. An associated profile without any modelId (its beacon id is not known
 * yet) also claims a beacon whose ids the registry does not know. A beacon with neither id is unknown.
 */
fun popupProfile(beacon: Beacon, registry: ProfileRegistry, associated: Profile?): Profile? {
    val ids = listOfNotNull(beacon.modelId, beacon.newModelId)
    if (ids.isEmpty()) return null
    for (id in ids) {
        registry.match(modelId = id).takeIf { it.id != ProfileRegistry.GENERIC.id }?.let { return it }
    }
    if (associated == null) return null
    if (ids.any { id -> associated.match.modelId.any { it.equals(id, ignoreCase = true) } }) return associated
    return associated.takeIf { it.match.modelId.isEmpty() }
}

fun isKnown(beacon: Beacon, registry: ProfileRegistry, associated: Profile?): Boolean =
    popupProfile(beacon, registry, associated) != null
