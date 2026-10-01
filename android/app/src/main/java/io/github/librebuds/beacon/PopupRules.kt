// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import io.github.librebuds.protocol.beacon.Beacon
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.ProfileRegistry

data class BeaconSighting(val address: String, val rssi: Int, val beacon: Beacon, val atMillis: Long)

enum class PopupDecision(val reason: String) {
    SHOW("shown"),
    IGNORE_NOT_CLOSE_RANGE("close range not enabled"),
    IGNORE_NOT_OPEN("case not open"),
    IGNORE_UNKNOWN("no matching bonded or added earbuds"),
    IGNORE_FAR("too far"),
    IGNORE_SHOWN("already shown for this opening"),
    IGNORE_COOLDOWN("cooldown"),
}

/**
 * Whether a beacon should raise the popup (spec 6.1). Close-range beacons (pairing mode) go through
 * [decide]; the everyday compact beacons through [decideCompact], which fires once per case opening.
 */
class PopupRules(
    private val cooldownMillis: Long = 30_000,
    private val margin: Int = 10,
    private val defaultReference: Int = -60,
    /**
     * Compact beacons carry no reference RSSI. Open cases in hardware round 2 came in at -52 to
     * -79 dBm from a phone close by; only earbuds bonded to this phone get this far, so it is lenient.
     */
    private val compactThreshold: Int = -80,
    /** Openings already show once each; this only stops a lid flapping open and shut. */
    private val compactCooldownMillis: Long = 5_000,
) {
    /**
     * A compact beacon: shows on the first frame of an [opening] that is near enough, then never
     * again for that opening. The opening starts whatever the signal, so a far first frame does not
     * use it up.
     */
    fun decideCompact(sighting: BeaconSighting, known: Boolean, opening: Opening?, lastShownAt: Long?): PopupDecision {
        if (!sighting.beacon.caseOpen || opening == null || !opening.open) return PopupDecision.IGNORE_NOT_OPEN
        if (!known) return PopupDecision.IGNORE_UNKNOWN
        if (opening.shown) return PopupDecision.IGNORE_SHOWN
        if (sighting.rssi < compactThreshold) return PopupDecision.IGNORE_FAR
        if (lastShownAt != null && sighting.atMillis - lastShownAt in 0 until compactCooldownMillis) return PopupDecision.IGNORE_COOLDOWN
        return PopupDecision.SHOW
    }

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
 * yet) also claims a beacon whose ids the registry does not know, unless [claimUnknown] is false. A
 * beacon with neither id is unknown.
 */
fun popupProfile(beacon: Beacon, registry: ProfileRegistry, associated: Profile?, claimUnknown: Boolean = true): Profile? {
    val ids = listOfNotNull(beacon.modelId, beacon.newModelId)
    if (ids.isEmpty()) return null
    for (id in ids) {
        registry.match(modelId = id).takeIf { it.id != ProfileRegistry.GENERIC.id }?.let { return it }
    }
    if (associated == null) return null
    if (ids.any { id -> associated.match.modelId.any { it.equals(id, ignoreCase = true) } }) return associated
    return associated.takeIf { claimUnknown && it.match.modelId.isEmpty() }
}

/**
 * The profile of a compact beacon, or null when the earbuds are not ours. Every FreeBuds in range sends
 * these all the time, so a model the registry knows is not enough: its profile must also be one of
 * the earbuds bonded to this phone ([bonded], profile ids) or the [associated] ones. When the bonded
 * devices cannot be read ([bonded] null, no permission) any known model counts, as before. The
 * associated profile never claims an unknown model here.
 */
fun compactProfile(beacon: Beacon, registry: ProfileRegistry, associated: Profile?, bonded: Set<String>?): Profile? {
    val profile = popupProfile(beacon, registry, associated, claimUnknown = false) ?: return null
    if (bonded == null || profile.id in bonded || profile.id == associated?.id) return profile
    return null
}

fun isKnown(beacon: Beacon, registry: ProfileRegistry, associated: Profile?): Boolean =
    popupProfile(beacon, registry, associated) != null
