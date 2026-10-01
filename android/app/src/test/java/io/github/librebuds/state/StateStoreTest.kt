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

    private fun roundTrip(
        state: BudsState,
        audioUp: (String) -> Boolean = { false },
        savedBoot: Int? = BOOT,
        currentBoot: Int? = BOOT,
    ): BudsState? = PersistedState.decode(PersistedState.from(state, savedBoot).encode())?.toBudsState(audioUp, currentBoot)

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
    fun sessionOnlyFieldsAreNotPersistedButDeviceDetailsAre() {
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
        assertEquals(full.copy(device = DeviceSummary(model = "FreeBuds 6", firmware = "1.0", serial = "X")), roundTrip(live))
    }

    @Test
    fun deviceDetailsSurviveARestart() {
        val live = full.copy(device = DeviceSummary(model = "FreeBuds 5", firmware = "5.0.0.208", serial = "TESTSERIAL000001"))
        assertEquals(DeviceSummary(model = "FreeBuds 5", firmware = "5.0.0.208", serial = "TESTSERIAL000001"), roundTrip(live)?.device)
    }

    @Test
    fun stateSavedWithoutDeviceDetailsStillLoads() {
        val old = PersistedState.from(full, BOOT).encode().replace(Regex(",\\s*\"(model|firmware|serial)\":\"[^\"]*\""), "")
        assertEquals(DeviceSummary(), PersistedState.decode(old)?.toBudsState({ false }, BOOT)?.device)
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
        val valid = PersistedState.from(full, BOOT).encode()
        assertNotNull(PersistedState.decode(valid))
        val unknownLink = valid.replace("\"link\":\"DISCONNECTED\"", "\"link\":\"SOMETHING_NEW\"")
        assertNotEquals(valid, unknownLink)
        assertNull(PersistedState.decode(unknownLink))
    }

    @Test
    fun extraUnknownKeyInAFullDocumentIsAFreshStart() {
        val valid = PersistedState.from(full, BOOT).encode()
        val extra = valid.replaceFirst("{", "{\"somethingNew\":1,")
        assertNull(PersistedState.decode(extra))
    }

    // Coordinator ruling on I2: a take-over from an earlier boot never survives, even with audio up.
    @Test
    fun takenOverFromTheSameBootWithAudioUpSurvives() {
        val restored = roundTrip(full.copy(link = LinkState.TAKEN_OVER), audioUp = { true }, savedBoot = BOOT, currentBoot = BOOT)!!
        assertEquals(LinkState.TAKEN_OVER, restored.link)
        assertFalse(shouldLaunchConnect(restored, full.address!!))
    }

    @Test
    fun takenOverFromAnEarlierBootComesBackDisconnectedEvenWithAudioUp() {
        val restored = roundTrip(full.copy(link = LinkState.TAKEN_OVER), audioUp = { true }, savedBoot = BOOT, currentBoot = BOOT + 1)!!
        assertEquals(LinkState.DISCONNECTED, restored.link)
        assertTrue(shouldLaunchConnect(restored, full.address!!))
    }

    @Test
    fun unknownBootCountLeavesItToAudio() {
        val takenOver = full.copy(link = LinkState.TAKEN_OVER)
        for ((saved, current) in listOf(null to BOOT, BOOT to null, null to null)) {
            assertEquals(LinkState.TAKEN_OVER, roundTrip(takenOver, audioUp = { true }, savedBoot = saved, currentBoot = current)?.link)
            assertEquals(LinkState.DISCONNECTED, roundTrip(takenOver, audioUp = { false }, savedBoot = saved, currentBoot = current)?.link)
        }
    }

    @Test
    fun formatOneWithoutBootCountIsAFreshStart() {
        val v1 = """{"version":1,"link":"TAKEN_OVER","address":"AA:BB:CC:DD:EE:FF","name":null,"profileId":"generic",""" +
            """"battery":null,"ancModeCode":null,"ancLevel":null,"updatedAtMillis":null}"""
        assertNull(PersistedState.decode(v1))
    }

    @Test
    fun oldOrOtherFormatIsAFreshStart() {
        // No version field: written by a build before the current format.
        assertNull(PersistedState.decode("""{"link":"TAKEN_OVER","address":"AA","profileId":"generic"}"""))
        val current = PersistedState.from(full, BOOT).encode()
        assertNotNull(PersistedState.decode(current))
        assertNull(PersistedState.decode(current.replace("\"version\":${PersistedState.VERSION}", "\"version\":${PersistedState.VERSION + 1}")))
    }

    private companion object {
        const val BOOT = 7
    }
}
