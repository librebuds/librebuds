// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import io.github.librebuds.protocol.beacon.FdeeBeacon
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.protocol.util.hexToBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupRulesTest {
    private val rules = PopupRules()
    private val popup = FdeeBeacon.parse("01 01 01 02 BA 03 00 01 55 0C E4 0D 50 0E 30".hexToBytes())!!
    private val noCloseRange = FdeeBeacon.parse("01 01 00 03 00 01 55".hexToBytes())!!
    private fun sighting(rssi: Int = -55, at: Long = 100_000L, beacon: io.github.librebuds.protocol.beacon.Beacon = popup) =
        BeaconSighting("AA:BB:CC:DD:EE:FF", rssi, beacon, at)

    @Test
    fun showsNearKnownCloseRangeBeacon() {
        assertEquals(PopupDecision.SHOW, rules.decide(sighting(), known = true, lastShownAt = null))
    }

    @Test
    fun ignoresBeaconWithoutCloseRange() {
        assertEquals(PopupDecision.IGNORE_NOT_CLOSE_RANGE, rules.decide(sighting(beacon = noCloseRange), known = true, lastShownAt = null))
    }

    @Test
    fun ignoresUnknownModelWhenNotAssociated() {
        assertEquals(PopupDecision.IGNORE_UNKNOWN, rules.decide(sighting(), known = false, lastShownAt = null))
    }

    @Test
    fun farBeaconIgnored() {
        // referenceRssi -70 in the beacon -> threshold -80
        assertEquals(PopupDecision.IGNORE_FAR, rules.decide(sighting(rssi = -85), known = true, lastShownAt = null))
        assertEquals(PopupDecision.SHOW, rules.decide(sighting(rssi = -80), known = true, lastShownAt = null))
    }

    @Test
    fun cooldownSuppressesRepeats() {
        assertEquals(PopupDecision.IGNORE_COOLDOWN, rules.decide(sighting(at = 110_000L), known = true, lastShownAt = 100_000L))
        assertEquals(PopupDecision.SHOW, rules.decide(sighting(at = 130_001L), known = true, lastShownAt = 100_000L))
    }

    @Test
    fun cooldownExpiresWhenClockMovedBack() {
        // The last popup is stamped "in the future": the wall clock was set back since then.
        assertEquals(PopupDecision.SHOW, rules.decide(sighting(at = 100_000L), known = true, lastShownAt = 110_000L))
    }

    private fun profile(json: String) = ProfileRegistry.fromJson(listOf(json)).profiles.single()
    private val freebuds6 = profile("""{"id":"freebuds-6","name":"FreeBuds 6","match":{"modelId":["000155"]}}""")
    private val associatedWithId = profile("""{"id":"assoc","name":"Assoc","match":{"modelId":["000155"]}}""")
    private val associatedWithoutId = profile("""{"id":"freebuds-pro-4","name":"FreeBuds Pro 4","match":{"btName":["HUAWEI FreeBuds Pro 4"]}}""")
    private val empty = ProfileRegistry(emptyList())

    // Close range, modelId 000155, newModelId 00ABCD (type 18 "ABCD").
    private val withNewId = FdeeBeacon.parse("01 01 01 03 00 01 55 12 41 42 43 44".hexToBytes())!!
    private val onlyNewId = FdeeBeacon.parse("01 01 01 12 41 42 43 44".hexToBytes())!!
    private val noIds = FdeeBeacon.parse("01 01 01 0C E4".hexToBytes())!!

    @Test
    fun knownWhenAssociatedOrProfileMatches() {
        val registry = ProfileRegistry(listOf(freebuds6))
        assertTrue(isKnown(popup, registry, associated = null))
        assertTrue(isKnown(popup, empty, associated = associatedWithId))
        assertFalse(isKnown(popup, empty, associated = null))
        assertEquals("freebuds-6", popupProfile(popup, registry, associated = null)?.id)
    }

    @Test
    fun newModelIdMatchesProfilesAndAssociation() {
        val registry = ProfileRegistry(listOf(profile("""{"id":"new","name":"New","match":{"modelId":["00abcd"]}}""")))
        // modelId 000155 is unknown here; the newModelId (case-insensitive) finds the profile.
        assertEquals("new", popupProfile(withNewId, registry, associated = null)?.id)
        assertEquals("new", popupProfile(onlyNewId, registry, associated = null)?.id)
        val associated = profile("""{"id":"assoc","name":"Assoc","match":{"modelId":["00ABCD"]}}""")
        assertEquals("assoc", popupProfile(onlyNewId, empty, associated)?.id)
    }

    @Test
    fun associationWithoutModelIdClaimsUnknownBeacons() {
        // The added earbuds' profile has no modelId yet: an unknown beacon is taken as theirs.
        assertEquals("freebuds-pro-4", popupProfile(popup, empty, associatedWithoutId)?.id)
        assertTrue(isKnown(onlyNewId, empty, associatedWithoutId))
        // A beacon the registry knows keeps its own profile.
        assertEquals("freebuds-6", popupProfile(popup, ProfileRegistry(listOf(freebuds6)), associatedWithoutId)?.id)
        // An association that lists other ids does not claim it; nor does any association without beacon ids.
        val other = profile("""{"id":"other","name":"Other","match":{"modelId":["000999"]}}""")
        assertFalse(isKnown(popup, empty, other))
        assertFalse(isKnown(noIds, empty, associatedWithoutId))
    }
}
