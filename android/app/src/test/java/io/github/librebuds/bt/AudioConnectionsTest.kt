// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.bt

import io.github.librebuds.companion.Presence
import io.github.librebuds.companion.trackPresence
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioConnectionsTest {
    private val address = "AA:BB:CC:DD:EE:FF"

    @After
    fun tearDown() {
        AclTracker.onDisconnected(address)
        AudioConnections.update(A2DP, emptyList())
        AudioConnections.update(HEADSET, emptyList())
    }

    @Test
    fun profileAnswerCountsAsAudioConnected() {
        assertFalse(isAudioConnected(address))
        AudioConnections.update(A2DP, listOf(address.lowercase()))
        assertTrue(isAudioConnected(address))
        AudioConnections.update(A2DP, emptyList())
        assertFalse(isAudioConnected(address))
    }

    @Test
    fun oneProfileDoesNotOverwriteAnother() {
        AudioConnections.update(A2DP, listOf(address))
        AudioConnections.update(HEADSET, emptyList())
        assertTrue(AudioConnections.contains(address))
    }

    @Test
    fun presenceSeedsAndClearsBothSources() {
        trackPresence(Presence.APPEARED, address)
        assertTrue(AclTracker.isConnected(address))
        AudioConnections.update(HEADSET, listOf(address))
        trackPresence(Presence.DISAPPEARED, address)
        assertFalse(isAudioConnected(address))
    }

    private companion object {
        // BluetoothProfile.A2DP and HEADSET; the android.jar stubs are not usable in JVM tests.
        const val A2DP = 2
        const val HEADSET = 1
    }
}
