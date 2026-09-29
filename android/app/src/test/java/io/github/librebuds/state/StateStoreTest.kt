// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.protocol.command.HostRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StateStoreTest {
    private val battery = BatteryState(74, 100, 84, 74, false, true, null)
    private val full = BudsState(
        link = LinkState.DISCONNECTED,
        address = "AA:BB:CC:DD:EE:FF",
        name = "HUAWEI FreeBuds 6",
        profileId = "freebuds-6",
        battery = battery,
        anc = AncState(modeCode = 1, level = 3),
        updatedAtMillis = 1_700_000_000_000,
    )

    private fun roundTrip(state: BudsState): BudsState? =
        PersistedState.decode(PersistedState.from(state).encode())?.toBudsState()

    @Test
    fun roundTripKeepsPersistedFields() {
        val restored = roundTrip(full)
        assertEquals(full, restored)
    }

    @Test
    fun roundTripKeepsMissingValuesMissing() {
        val restored = roundTrip(BudsState(address = "AA", battery = BatteryState(null, 50, null, null, null, null, null)))
        assertEquals(BudsState(address = "AA", battery = BatteryState(null, 50, null, null, null, null, null)), restored)
        assertNull(roundTrip(BudsState())!!.address)
    }

    @Test
    fun liveLinkStatesComeBackDisconnected() {
        assertEquals(LinkState.DISCONNECTED, roundTrip(full.copy(link = LinkState.CONNECTED))?.link)
        assertEquals(LinkState.DISCONNECTED, roundTrip(full.copy(link = LinkState.CONNECTING))?.link)
    }

    @Test
    fun takenOverSurvives() {
        assertEquals(LinkState.TAKEN_OVER, roundTrip(full.copy(link = LinkState.TAKEN_OVER))?.link)
    }

    @Test
    fun sessionOnlyFieldsAreNotPersisted() {
        val live = full.copy(
            link = LinkState.CONNECTED,
            capabilities = setOf("battery", "anc", "wear"),
            device = DeviceSummary(model = "FreeBuds 6", firmware = "1.0", serial = "X"),
            lastError = LinkError.NO_REPLY,
            settings = DeviceSettings(wearDetection = true, unanswered = setOf("equalizer")),
            hosts = listOf(HostRow(index = 0, count = 1, mac = "11:22:33:44:55:66", name = "Laptop", connection = 1, preferred = false, autoConnect = null)),
            multipointEnabled = true,
            inEar = true,
        )
        assertEquals(full, roundTrip(live))
    }

    @Test
    fun corruptJsonIsAFreshStart() {
        assertNull(PersistedState.decode(null))
        assertNull(PersistedState.decode(""))
        assertNull(PersistedState.decode("{not json"))
        assertNull(PersistedState.decode("[]"))
        assertNull(PersistedState.decode("""{"version":1,"link":"SOMETHING_NEW","profileId":"generic"}"""))
    }

    @Test
    fun oldOrOtherFormatIsAFreshStart() {
        // No version field: written by a build before the current format.
        assertNull(PersistedState.decode("""{"link":"TAKEN_OVER","address":"AA","profileId":"generic"}"""))
        val current = PersistedState.from(full).encode()
        assertNotNull(PersistedState.decode(current))
        assertNull(PersistedState.decode(current.replace("\"version\":${PersistedState.VERSION}", "\"version\":${PersistedState.VERSION + 1}")))
    }
}
