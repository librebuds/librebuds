// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.protocol.command.HostRow
import io.github.librebuds.service.shouldLaunchConnect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun roundTrip(state: BudsState, audioUp: (String) -> Boolean = { false }): BudsState? =
        PersistedState.decode(PersistedState.from(state).encode())?.toBudsState(audioUp)

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

    // Final review I2: a take-over outlives a restart only while the phone still has audio to the earbuds.
    @Test
    fun takenOverSurvivesWhileAudioIsUp() {
        val restored = roundTrip(full.copy(link = LinkState.TAKEN_OVER), audioUp = { it == full.address })!!
        assertEquals(LinkState.TAKEN_OVER, restored.link)
        assertFalse(shouldLaunchConnect(restored, full.address!!))
    }

    @Test
    fun takenOverWithoutAudioComesBackDisconnected() {
        val restored = roundTrip(full.copy(link = LinkState.TAKEN_OVER), audioUp = { false })!!
        assertEquals(LinkState.DISCONNECTED, restored.link)
        assertTrue(shouldLaunchConnect(restored, full.address!!))
        // Audio to some other device does not count.
        assertEquals(LinkState.DISCONNECTED, roundTrip(full.copy(link = LinkState.TAKEN_OVER), audioUp = { it == "11:22:33:44:55:66" })?.link)
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

    // Task 6 review #4: complete, otherwise valid documents, so only the one odd field decides.
    @Test
    fun unknownLinkValueInAFullDocumentIsAFreshStart() {
        val valid = PersistedState.from(full).encode()
        assertNotNull(PersistedState.decode(valid))
        val unknownLink = valid.replace("\"link\":\"DISCONNECTED\"", "\"link\":\"SOMETHING_NEW\"")
        assertNotEquals(valid, unknownLink)
        assertNull(PersistedState.decode(unknownLink))
    }

    @Test
    fun extraUnknownKeyInAFullDocumentIsAFreshStart() {
        val valid = PersistedState.from(full).encode()
        val extra = valid.replaceFirst("{", "{\"somethingNew\":1,")
        assertNull(PersistedState.decode(extra))
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
