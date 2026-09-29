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

    @Test
    fun knownWhenAssociatedOrProfileMatches() {
        val registry = ProfileRegistry.fromJson(listOf("""{"id":"freebuds-6","name":"FreeBuds 6","match":{"modelId":["000155"]}}"""))
        assertTrue(isKnown(popup, registry, associatedModelIds = emptySet()))
        assertTrue(isKnown(popup, ProfileRegistry(emptyList()), associatedModelIds = setOf("000155")))
        assertFalse(isKnown(popup, ProfileRegistry(emptyList()), associatedModelIds = emptySet()))
    }
}
