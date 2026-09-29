// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import io.github.librebuds.protocol.beacon.Beacon
import io.github.librebuds.protocol.beacon.FdeeBeacon
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.ProfileRegistry

/** One scan result as the receiver gets it: address, signal and the raw 0xFDEE service data (if any). */
class RawSighting(val address: String, val rssi: Int, val serviceData: ByteArray?)

data class BeaconVerdict(val sighting: BeaconSighting, val decision: PopupDecision)

/**
 * The cooldown key of a beacon: its model, not its BLE address, which earbuds may rotate. Two pairs of
 * the same model therefore share one cooldown.
 */
fun cooldownKey(beacon: Beacon): String = "${beacon.modelId ?: "-"}/${beacon.subModelId ?: 0}"

/**
 * Decides every parsable result of one scan batch at wall-clock [now]. A popup shown for a key earlier
 * in the batch puts the key's later sightings in cooldown; [lastShownAt] gives the stored times.
 * [associated] is the profile of the earbuds added in the app, if any (see [popupProfile]).
 */
fun judgeBatch(
    results: List<RawSighting>,
    now: Long,
    rules: PopupRules,
    registry: ProfileRegistry,
    associated: Profile?,
    lastShownAt: (String) -> Long?,
): List<BeaconVerdict> {
    val shownInBatch = mutableMapOf<String, Long>()
    return results.mapNotNull { result ->
        val beacon = result.serviceData?.let(FdeeBeacon::parse) ?: return@mapNotNull null
        val sighting = BeaconSighting(result.address, result.rssi, beacon, now)
        val key = cooldownKey(beacon)
        val known = isKnown(beacon, registry, associated)
        val decision = rules.decide(sighting, known, shownInBatch[key] ?: lastShownAt(key))
        if (decision == PopupDecision.SHOW) shownInBatch[key] = now
        BeaconVerdict(sighting, decision)
    }
}
