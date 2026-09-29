// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.bt

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AclTrackerTest {
    private val address = "AA:BB:CC:DD:EE:FF"

    @After
    fun tearDown() {
        AclTracker.onDisconnected(address)
        AclTracker.onDisconnected(address.lowercase())
    }

    @Test
    fun connectThenDisconnect() {
        assertFalse(AclTracker.isConnected(address))
        AclTracker.onConnected(address)
        assertTrue(AclTracker.isConnected(address))
        AclTracker.onDisconnected(address)
        assertFalse(AclTracker.isConnected(address))
    }

    @Test
    fun addressLookupIsCaseInsensitive() {
        AclTracker.onConnected(address.lowercase())
        assertTrue(AclTracker.isConnected(address))
        assertTrue(AclTracker.isConnected(address.lowercase()))

        AclTracker.onDisconnected(address)
        assertFalse(AclTracker.isConnected(address.lowercase()))
    }
}
