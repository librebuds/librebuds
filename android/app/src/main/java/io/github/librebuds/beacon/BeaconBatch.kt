// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import io.github.librebuds.protocol.beacon.Beacon
import io.github.librebuds.protocol.beacon.BeaconFormat
import io.github.librebuds.protocol.beacon.FdeeBeacon
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.ProfileRegistry

/** One scan result as the receiver gets it: address, signal and the raw 0xFDEE service data (if any). */
class RawSighting(val address: String, val rssi: Int, val serviceData: ByteArray?)

/**
 * [profile] is the model the beacon names (null when unknown; on a SHOW, the earbuds to show);
 * [batteries] what the popup
 * shows, for a compact beacon the levels gathered over the whole opening.
 */
data class BeaconVerdict(
    val sighting: BeaconSighting,
    val decision: PopupDecision,
    val profile: Profile? = null,
    val batteries: BudBatteries = BudBatteries(sighting.beacon.left, sighting.beacon.right, sighting.beacon.case),
)

/**
 * The cooldown key of a beacon: its model, not its BLE address, which earbuds may rotate. Two pairs of
 * the same model therefore share one cooldown.
 */
fun cooldownKey(beacon: Beacon): String = "${beacon.modelId ?: "-"}/${beacon.subModelId ?: 0}"

/**
 * Decides every parsable result of one scan batch at wall-clock [now]. A popup shown for a key earlier
 * in the batch puts the key's later sightings in cooldown; [lastShownAt] gives the stored times.
 * [associated] is the profile of the earbuds added in the app, if any (see [popupProfile]); [bonded]
 * the profile ids of the earbuds bonded to the phone, null when they cannot be read.
 *
 * Compact beacons update [openings] (per cooldown key, see [CaseOpenings]) and are decided by
 * [PopupRules.decideCompact]; close-range ones by [PopupRules.decide].
 */
fun judgeBatch(
    results: List<RawSighting>,
    now: Long,
    rules: PopupRules,
    registry: ProfileRegistry,
    associated: Profile?,
    lastShownAt: (String) -> Long?,
    openings: MutableMap<String, Opening> = mutableMapOf(),
    bonded: Set<String>? = null,
): List<BeaconVerdict> {
    val shownInBatch = mutableMapOf<String, Long>()
    return results.mapNotNull { result ->
        val beacon = result.serviceData?.let(FdeeBeacon::parse) ?: return@mapNotNull null
        val sighting = BeaconSighting(result.address, result.rssi, beacon, now)
        val key = cooldownKey(beacon)
        val lastShown = shownInBatch[key] ?: lastShownAt(key)
        val verdict = if (beacon.format == BeaconFormat.COMPACT) {
            val opening = CaseOpenings.step(openings[key], beacon, now)
            if (opening != null) openings[key] = opening else openings.remove(key)
            val known = compactProfile(beacon, registry, associated, bonded) != null
            val profile = popupProfile(beacon, registry, associated, claimUnknown = false)
            val decision = rules.decideCompact(sighting, known, opening, lastShown)
            if (decision == PopupDecision.SHOW) openings[key] = opening!!.copy(shown = true)
            BeaconVerdict(sighting, decision, profile, opening?.batteries ?: BudBatteries())
        } else {
            val profile = popupProfile(beacon, registry, associated)
            BeaconVerdict(sighting, rules.decide(sighting, profile != null, lastShown), profile)
        }
        if (verdict.decision == PopupDecision.SHOW) shownInBatch[key] = now
        verdict
    }
}

/**
 * Logcat lines for one batch, at most one per cooldown key: the popup when one was shown, otherwise
 * the last reason it was ignored. A reason already logged for the key ([lastLogged], updated here) is
 * not repeated, so an open case does not flood the log. Never contains an address.
 */
fun decisionLogLines(verdicts: List<BeaconVerdict>, lastLogged: MutableMap<String, PopupDecision>): List<String> =
    verdicts.groupBy { cooldownKey(it.sighting.beacon) }.mapNotNull { (key, group) ->
        val verdict = group.firstOrNull { it.decision == PopupDecision.SHOW } ?: group.last()
        val decision = verdict.decision
        if (decision != PopupDecision.SHOW && lastLogged[key] == decision) return@mapNotNull null
        lastLogged[key] = decision
        val beacon = verdict.sighting.beacon
        val model = "${verdict.profile?.name ?: "unknown model"} [$key ${beacon.format.name.lowercase()}" +
            (beacon.lid?.let { " ${it.name.lowercase()}" } ?: "") + ", ${verdict.sighting.rssi} dBm]"
        if (decision == PopupDecision.SHOW) "popup shown for $model" else "ignored: ${decision.reason}: $model"
    }
