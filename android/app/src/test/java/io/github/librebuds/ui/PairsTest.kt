// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import io.github.librebuds.companion.DetectedBuds
import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkState
import io.github.librebuds.state.DEMO_ADDRESS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairsTest {
    private val address = "AA:BB:CC:DD:EE:FF"
    private val bud = DetectedBuds(name = "HUAWEI FreeBuds 6", address = address, profileId = "generic", model = "FreeBuds 6", bondedName = "HUAWEI FreeBuds 6")
    private val battery = BatteryState(80, 80, 70, 60, false, false, false)
    private val noProfiles: (String) -> Profile? = { null }

    private fun detection(connected: Boolean) = Detection(
        buds = listOf(bud),
        connected = if (connected) setOf(address.uppercase()) else emptySet(),
        permissionMissing = false,
    )

    @Test
    fun connectedRowShowsLiveBatteryAndNoLastSeen() {
        val state = BudsState(link = LinkState.CONNECTED, address = address, battery = battery, updatedAtMillis = 1_000L)

        val row = pairRows(detection(connected = false), state, showDemo = false, profileOf = noProfiles).single()

        assertTrue(row.connected)
        assertEquals("L 80% · R 70% · Case 60%", row.battery)
        assertNull(row.lastSeenMillis)
    }

    @Test
    fun disconnectedRowShowsLastSeenAndNoBattery() {
        val state = BudsState(link = LinkState.DISCONNECTED, address = address, battery = battery, updatedAtMillis = 2_000L)

        val row = pairRows(detection(connected = false), state, showDemo = false, profileOf = noProfiles).single()

        assertTrue(!row.connected)
        assertNull(row.battery)
        assertEquals(2_000L, row.lastSeenMillis)
    }

    @Test
    fun takenOverByAnotherDeviceIsTreatedAsNotConnected() {
        val state = BudsState(link = LinkState.TAKEN_OVER, address = address, battery = battery, updatedAtMillis = 3_000L)

        val row = pairRows(detection(connected = false), state, showDemo = false, profileOf = noProfiles).single()

        assertTrue(!row.connected)
        assertNull(row.battery)
        assertEquals(3_000L, row.lastSeenMillis)
    }

    @Test
    fun disconnectedRowWithNothingKnownYetShowsNeitherBatteryNorLastSeen() {
        val state = BudsState(link = LinkState.DISCONNECTED, address = address, battery = null, updatedAtMillis = null)

        val row = pairRows(detection(connected = false), state, showDemo = false, profileOf = noProfiles).single()

        assertNull(row.battery)
        assertNull(row.lastSeenMillis)
    }

    @Test
    fun aBudTheControllerNeverHeldHasNoLastKnownDataEvenIfAnotherPairsStateCarriesATimestamp() {
        // state tracks a different address; its battery/updatedAtMillis must not leak onto this row.
        val state = BudsState(link = LinkState.CONNECTED, address = "11:22:33:44:55:66", battery = battery, updatedAtMillis = 4_000L)

        val row = pairRows(detection(connected = false), state, showDemo = false, profileOf = noProfiles).single()

        assertNull(row.battery)
        assertNull(row.lastSeenMillis)
    }

    @Test
    fun audioConnectedButNotTheCurrentlyTrackedPairShowsConnectedWithoutStaleData() {
        // A2DP is up for this bud (detection says connected), but the controller link is held by a
        // different address, so there is no battery reading to show and no "last seen" time either.
        val state = BudsState(link = LinkState.CONNECTED, address = "11:22:33:44:55:66", battery = battery, updatedAtMillis = 5_000L)

        val row = pairRows(detection(connected = true), state, showDemo = false, profileOf = noProfiles).single()

        assertTrue(row.connected)
        assertNull(row.battery)
        assertNull(row.lastSeenMillis)
    }

    private fun row(address: String, connected: Boolean, name: String = address) =
        PairRow(address = address, name = name, model = null, connected = connected, battery = null)

    private val a = "AA:AA:AA:AA:AA:AA"
    private val b = "BB:BB:BB:BB:BB:BB"
    private val c = "CC:CC:CC:CC:CC:CC"

    @Test
    fun startPairIsNoneWithoutPairedFreeBuds() {
        assertNull(chooseStartPair(emptyList(), emptyMap(), BudsState()))
    }

    @Test
    fun startPairPrefersAConnectedPairOverTheLastUsedOne() {
        val pairs = listOf(row(a, connected = false), row(b, connected = true))
        val lastUsedA = BudsState(link = LinkState.DISCONNECTED, address = a)
        assertEquals(b, chooseStartPair(pairs, mapOf(a to 900L, b to 100L), lastUsedA))
    }

    @Test
    fun startPairAmongSeveralConnectedIsTheMostRecentlyConnected() {
        val pairs = listOf(row(a, true), row(b, true), row(c, true))
        assertEquals(c, chooseStartPair(pairs, mapOf(a to 1L, b to 2L, c to 3L), BudsState()))
    }

    @Test
    fun startPairAmongSeveralConnectedIsTheOneTheControllerHolds() {
        // The controller already talks to a; showing b would show a pair it does not control.
        val pairs = listOf(row(a, true), row(b, true))
        for (link in listOf(LinkState.CONNECTED, LinkState.CONNECTING, LinkState.TAKEN_OVER)) {
            assertEquals(link.name, a, chooseStartPair(pairs, mapOf(a to 1L, b to 2L), BudsState(link = link, address = a)))
        }
        // A controller that lost its link does not count as holding the pair.
        assertEquals(b, chooseStartPair(pairs, mapOf(a to 1L, b to 2L), BudsState(link = LinkState.DISCONNECTED, address = a)))
    }

    @Test
    fun startPairWithNoneConnectedIsTheLastUsed() {
        val pairs = listOf(row(a, false), row(b, false), row(c, false))
        assertEquals(a, chooseStartPair(pairs, mapOf(b to 5L, c to 9L), BudsState(link = LinkState.DISCONNECTED, address = a.lowercase())))
    }

    @Test
    fun startPairWithNoneConnectedAndNoLastUsedIsTheMostRecentlyConnectedThenTheFirst() {
        val pairs = listOf(row(a, false), row(b, false), row(c, false))
        assertEquals(b, chooseStartPair(pairs, mapOf(b to 5L, c to 2L), BudsState()))
        assertEquals(a, chooseStartPair(pairs, emptyMap(), BudsState()))
        // A last used pair that is no longer paired is skipped.
        assertEquals(c, chooseStartPair(pairs, mapOf(c to 2L), BudsState(address = "DD:DD:DD:DD:DD:DD")))
    }

    @Test
    fun switcherListsConnectedPairsFirstThenByNameAndMarksTheSelectedOne() {
        val buds = listOf(
            DetectedBuds("Alpha", a, "generic", null, "Alpha"),
            DetectedBuds("beta", b, "generic", null, "beta"),
            DetectedBuds("Gamma", c, "generic", null, "Gamma"),
        )
        val detection = Detection(buds, connected = setOf(c), permissionMissing = false)

        val rows = pairRows(detection, BudsState(), showDemo = false, profileOf = noProfiles, selected = b.lowercase())

        assertEquals(listOf(c, a, b), rows.map { it.address })
        assertEquals(listOf(true, false, false), rows.map { it.connected })
        assertEquals(listOf(false, false, true), rows.map { it.selected })
    }

    @Test
    fun switcherShowsTheDemoPairFirstInDemoMode() {
        val demoState = BudsState(link = LinkState.CONNECTED, address = DEMO_ADDRESS, battery = battery)
        val rows = pairRows(detection(connected = false), demoState, showDemo = true, profileOf = noProfiles)
        assertEquals(DEMO_ADDRESS, rows.first().address)
        assertTrue(rows.first().connected)
        assertFalse(rows[1].connected)
        assertEquals(DEMO_ADDRESS, chooseStartPair(rows, emptyMap(), demoState))
    }

    @Test
    fun descriptionShowsBatteryWhileConnectedAndLastSeenOtherwise() {
        assertEquals("FreeBuds 6 · Connected · L 100%", pairDescriptionText("FreeBuds 6", "Connected", "L 100%", "last seen 14:02"))
        assertEquals("FreeBuds 6 · Not connected · last seen 14:02", pairDescriptionText("FreeBuds 6", "Not connected", null, "last seen 14:02"))
        assertEquals("Unknown model · Not connected", pairDescriptionText("Unknown model", "Not connected", null, null))
    }
}
