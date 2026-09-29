// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.service

import io.github.librebuds.state.LinkState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceRulesTest {
    @Test
    fun initialDisconnectedStateKeepsService() {
        assertFalse(serviceShouldStop(previous = null, current = LinkState.DISCONNECTED, hadConnected = false))
    }

    @Test
    fun startingToConnectKeepsService() {
        assertFalse(serviceShouldStop(previous = LinkState.DISCONNECTED, current = LinkState.CONNECTING, hadConnected = false))
    }

    @Test
    fun failedFirstConnectStopsService() {
        assertTrue(serviceShouldStop(previous = LinkState.CONNECTING, current = LinkState.DISCONNECTED, hadConnected = false))
    }

    @Test
    fun droppedLinkStopsService() {
        assertTrue(serviceShouldStop(previous = LinkState.CONNECTED, current = LinkState.DISCONNECTED, hadConnected = true))
    }

    @Test
    fun takenOverKeepsService() {
        assertFalse(serviceShouldStop(previous = LinkState.CONNECTED, current = LinkState.TAKEN_OVER, hadConnected = true))
    }

    @Test
    fun disconnectAfterTakeOverStopsService() {
        assertTrue(serviceShouldStop(previous = LinkState.TAKEN_OVER, current = LinkState.DISCONNECTED, hadConnected = true))
    }

    @Test
    fun connectedKeepsService() {
        assertFalse(serviceShouldStop(previous = LinkState.CONNECTING, current = LinkState.CONNECTED, hadConnected = true))
    }
}
