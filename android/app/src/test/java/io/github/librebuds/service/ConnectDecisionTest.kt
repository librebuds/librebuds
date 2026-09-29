// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.service

import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectDecisionTest {
    private val address = "AA:BB:CC:DD:EE:FF"

    @Test
    fun disconnectedLaunchesConnect() {
        assertTrue(shouldLaunchConnect(BudsState(link = LinkState.DISCONNECTED, address = address), address))
        assertTrue(shouldLaunchConnect(BudsState(), address))
    }

    @Test
    fun connectingOrConnectedToSameAddressDoesNotLaunch() {
        assertFalse(shouldLaunchConnect(BudsState(link = LinkState.CONNECTING, address = address), address))
        assertFalse(shouldLaunchConnect(BudsState(link = LinkState.CONNECTED, address = address), "aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun takenOverSameAddressDoesNotLaunch() {
        // Only an explicit takeOver() may take the earbuds back from another device.
        assertFalse(shouldLaunchConnect(BudsState(link = LinkState.TAKEN_OVER, address = address), address))
    }

    @Test
    fun otherAddressLaunchesConnect() {
        val other = "11:22:33:44:55:66"
        assertTrue(shouldLaunchConnect(BudsState(link = LinkState.CONNECTED, address = other), address))
        assertTrue(shouldLaunchConnect(BudsState(link = LinkState.TAKEN_OVER, address = other), address))
    }
}
