// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import io.github.librebuds.companion.DetectedBuds
import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRowsTest {
    private val address = "AA:BB:CC:DD:EE:FF"
    private val bud = DetectedBuds(name = "HUAWEI FreeBuds 6", address = address, profileId = "generic", model = "FreeBuds 6", bondedName = "HUAWEI FreeBuds 6")
    private val battery = BatteryState(80, 80, 70, 60, false, false, false)
    private val noProfiles: (String) -> Profile? = { null }

    private fun detection(connected: Boolean) = Detection(
        buds = listOf(bud),
        connected = if (connected) setOf(address.uppercase()) else emptySet(),
        permissionMissing = false,
        settled = true,
    )

    @Test
    fun connectedRowShowsLiveBatteryAndNoLastSeen() {
        val state = BudsState(link = LinkState.CONNECTED, address = address, battery = battery, updatedAtMillis = 1_000L)

        val row = homeRows(detection(connected = false), state, showDemo = false, profileOf = noProfiles).single()

        assertTrue(row.connected)
        assertEquals("L 80% · R 70% · Case 60%", row.battery)
        assertNull(row.lastSeenMillis)
    }

    @Test
    fun disconnectedRowShowsLastSeenAndNoBattery() {
        val state = BudsState(link = LinkState.DISCONNECTED, address = address, battery = battery, updatedAtMillis = 2_000L)

        val row = homeRows(detection(connected = false), state, showDemo = false, profileOf = noProfiles).single()

        assertTrue(!row.connected)
        assertNull(row.battery)
        assertEquals(2_000L, row.lastSeenMillis)
    }

    @Test
    fun takenOverByAnotherDeviceIsTreatedAsNotConnected() {
        val state = BudsState(link = LinkState.TAKEN_OVER, address = address, battery = battery, updatedAtMillis = 3_000L)

        val row = homeRows(detection(connected = false), state, showDemo = false, profileOf = noProfiles).single()

        assertTrue(!row.connected)
        assertNull(row.battery)
        assertEquals(3_000L, row.lastSeenMillis)
    }

    @Test
    fun disconnectedRowWithNothingKnownYetShowsNeitherBatteryNorLastSeen() {
        val state = BudsState(link = LinkState.DISCONNECTED, address = address, battery = null, updatedAtMillis = null)

        val row = homeRows(detection(connected = false), state, showDemo = false, profileOf = noProfiles).single()

        assertNull(row.battery)
        assertNull(row.lastSeenMillis)
    }

    @Test
    fun aBudTheControllerNeverHeldHasNoLastKnownDataEvenIfAnotherPairsStateCarriesATimestamp() {
        // state tracks a different address; its battery/updatedAtMillis must not leak onto this row.
        val state = BudsState(link = LinkState.CONNECTED, address = "11:22:33:44:55:66", battery = battery, updatedAtMillis = 4_000L)

        val row = homeRows(detection(connected = false), state, showDemo = false, profileOf = noProfiles).single()

        assertNull(row.battery)
        assertNull(row.lastSeenMillis)
    }

    @Test
    fun audioConnectedButNotTheCurrentlyTrackedPairShowsConnectedWithoutStaleData() {
        // A2DP is up for this bud (detection says connected), but the controller link is held by a
        // different address, so there is no battery reading to show and no "last seen" time either.
        val state = BudsState(link = LinkState.CONNECTED, address = "11:22:33:44:55:66", battery = battery, updatedAtMillis = 5_000L)

        val row = homeRows(detection(connected = true), state, showDemo = false, profileOf = noProfiles).single()

        assertTrue(row.connected)
        assertNull(row.battery)
        assertNull(row.lastSeenMillis)
    }
}
