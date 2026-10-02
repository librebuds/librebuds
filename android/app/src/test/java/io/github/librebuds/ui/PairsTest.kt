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
    fun connectedRowShowsLiveBatteryInFullColour() {
        val state = BudsState(link = LinkState.CONNECTED, address = address, battery = battery, updatedAtMillis = 1_000L)

        val row = pairRows(detection(connected = false), state, showDemo = false, profileOf = noProfiles).single()

        assertTrue(row.connected)
        assertEquals("L 80% · R 70% · Case 60%", row.battery)
        assertFalse(row.greyed)
    }

    @Test
    fun disconnectedRowIsGreyedWithoutBattery() {
        val state = BudsState(link = LinkState.DISCONNECTED, address = address, battery = battery, updatedAtMillis = 2_000L)

        val row = pairRows(detection(connected = false), state, showDemo = false, profileOf = noProfiles).single()

        assertTrue(!row.connected)
        assertTrue(row.greyed)
        assertNull(row.battery)
    }

    @Test
    fun takenOverByAnotherDeviceIsTreatedAsNotConnected() {
        val state = BudsState(link = LinkState.TAKEN_OVER, address = address, battery = battery, updatedAtMillis = 3_000L)

        val row = pairRows(detection(connected = false), state, showDemo = false, profileOf = noProfiles).single()

        assertTrue(!row.connected)
        assertTrue(row.greyed)
        assertNull(row.battery)
    }

    @Test
    fun disconnectedRowWithNothingKnownYetShowsNoBattery() {
        val state = BudsState(link = LinkState.DISCONNECTED, address = address, battery = null, updatedAtMillis = null)

        val row = pairRows(detection(connected = false), state, showDemo = false, profileOf = noProfiles).single()

        assertNull(row.battery)
    }

    @Test
    fun aBudTheControllerNeverHeldHasNoLastKnownDataEvenIfAnotherPairsStateCarriesATimestamp() {
        // state tracks a different address; its battery/updatedAtMillis must not leak onto this row.
        val state = BudsState(link = LinkState.CONNECTED, address = "11:22:33:44:55:66", battery = battery, updatedAtMillis = 4_000L)

        val row = pairRows(detection(connected = false), state, showDemo = false, profileOf = noProfiles).single()

        assertNull(row.battery)
    }

    @Test
    fun audioConnectedButNotTheCurrentlyTrackedPairShowsConnectedWithoutStaleData() {
        // A2DP is up for this bud (detection says connected), but the controller link is held by a
        // different address, so there is no battery reading to show and no "last seen" time either.
        val state = BudsState(link = LinkState.CONNECTED, address = "11:22:33:44:55:66", battery = battery, updatedAtMillis = 5_000L)

        val row = pairRows(detection(connected = true), state, showDemo = false, profileOf = noProfiles).single()

        assertTrue(row.connected)
        assertNull(row.battery)
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

    private fun buds(vararg names: Pair<String, String>) = names.map { (address, name) -> DetectedBuds(name, address, "generic", null, name) }

    @Test
    fun listShowsConnectedPairsOnTopThenTheOthersMostRecentlyConnectedFirst() {
        val d = "DD:DD:DD:DD:DD:DD"
        val detection = Detection(
            buds = buds(a to "Alpha", b to "beta", c to "Gamma", d to "Delta"),
            connected = setOf(c, d),
            permissionMissing = false,
            lastConnectedAt = mapOf(a to 10L, b to 30L, c to 5L, d to 7L),
        )

        val rows = pairRows(detection, BudsState(), showDemo = false, profileOf = noProfiles)

        assertEquals(listOf(d, c, b, a), rows.map { it.address })
        assertEquals(listOf(false, false, true, true), rows.map { it.greyed })
    }

    @Test
    fun listOrderWithinAGroupFallsBackToNameThenAddressSoItIsStable() {
        val twin = "AB:AB:AB:AB:AB:AB"
        val detection = Detection(
            buds = buds(c to "Gamma", b to "beta", twin to "Alpha", a to "Alpha"),
            connected = emptySet(),
            permissionMissing = false,
            lastConnectedAt = mapOf(c to 50L),
        )

        val rows = pairRows(detection, BudsState(), showDemo = false, profileOf = noProfiles)

        // Gamma connected most recently; the never connected follow by name, the same name by address.
        assertEquals(listOf(c, a, twin, b), rows.map { it.address })
        // Reading again in another order gives the same list.
        val shuffled = detection.copy(buds = detection.buds.reversed())
        assertEquals(rows.map { it.address }, pairRows(shuffled, BudsState(), showDemo = false, profileOf = noProfiles).map { it.address })
    }

    @Test
    fun thePairTheControllerHoldsCountsAsConnectedForTheList() {
        val detection = Detection(buds = buds(a to "Alpha", b to "beta"), connected = emptySet(), permissionMissing = false, lastConnectedAt = mapOf(a to 9L))
        val rows = pairRows(detection, BudsState(link = LinkState.CONNECTED, address = b.lowercase(), battery = battery), showDemo = false, profileOf = noProfiles)
        assertEquals(listOf(b, a), rows.map { it.address })
        assertEquals(listOf(false, true), rows.map { it.greyed })
    }

    @Test
    fun theDemoPairStaysFirstInDemoMode() {
        val demoState = BudsState(link = LinkState.CONNECTED, address = DEMO_ADDRESS, battery = battery)
        val detection = Detection(buds = buds(a to "Alpha", b to "beta"), connected = setOf(b), permissionMissing = false)
        val rows = pairRows(detection, demoState, showDemo = true, profileOf = noProfiles)
        assertEquals(listOf(DEMO_ADDRESS, b, a), rows.map { it.address })
        assertEquals(listOf(false, false, true), rows.map { it.greyed })
        assertEquals(DEMO_ADDRESS, chooseStartPair(rows, emptyMap(), demoState))
    }

    @Test
    fun startOpensTheLaunchPairWhenItIsKnown() {
        val pairs = listOf(row(a, connected = true), row(b, connected = false))
        assertEquals(b, startPair(pairs, launchAddress = b.lowercase())?.address)
        // An unknown launch pair is ignored and the list shows.
        assertNull(startPair(pairs, launchAddress = "DD:DD:DD:DD:DD:DD"))
    }

    @Test
    fun plainStartAlwaysShowsTheList() {
        // Connected, connecting or a single known pair: the app still starts on the list.
        assertNull(startPair(listOf(row(a, connected = false), row(b, connected = true))))
        assertNull(startPair(listOf(row(a, connected = false))))
        assertNull(startPair(emptyList()))
    }

    @Test
    fun descriptionShowsTheBatteryOnlyWhileConnected() {
        assertEquals("FreeBuds 6 · L 100%", pairDescriptionText("FreeBuds 6", "L 100%"))
        // Greyed rows say nothing about not being connected; the grey does.
        assertEquals("FreeBuds 6", pairDescriptionText("FreeBuds 6", null))
    }
}
