// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import io.github.librebuds.protocol.beacon.FdeeBeacon
import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.protocol.util.hexToBytes
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkState
import io.github.librebuds.ui.model.Battery
import io.github.librebuds.ui.model.BatteryComponent
import io.github.librebuds.ui.model.BatteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupModelTest {
    private val profile = ProfileRegistry.fromJson(listOf("""{"id":"freebuds-6","name":"FreeBuds 6","art":"round"}""")).profiles.single()

    @Test
    fun popupModelFromBeacon() {
        val beacon = FdeeBeacon.parse("01 01 01 03 00 01 55 0C E4 0D 50 0E 30".hexToBytes())!!
        val model = popupModel(beacon, profile)
        assertEquals("FreeBuds 6", model.title)
        assertEquals("round", model.art)
        assertEquals(
            listOf(
                Battery(BatteryComponent.LEFT, 100, BatteryStatus.CHARGING),
                Battery(BatteryComponent.RIGHT, 80, BatteryStatus.NOT_CHARGING),
                Battery(BatteryComponent.CASE, 48, BatteryStatus.NOT_CHARGING),
            ),
            model.batteries,
        )
    }

    @Test
    fun popupModelFromBeaconWithoutBattery() {
        val beacon = FdeeBeacon.parse("01 01 01 03 00 01 55".hexToBytes())!!
        assertTrue(popupModel(beacon, profile).batteries.isEmpty())
    }

    @Test
    fun liveStateReplacesBeaconBattery() {
        val beacon = FdeeBeacon.parse("01 01 01 03 00 01 55".hexToBytes())!!
        val state = BudsState(link = LinkState.CONNECTED, battery = BatteryState(70, 70, 60, 40, false, false, false), profileId = "freebuds-6")
        val model = popupModel(beacon, profile).withState(state)
        assertTrue(model.connected)
        assertEquals(3, model.batteries.size)
    }

    @Test
    fun liveStateOfOtherEarbudsIgnored() {
        // A stranger's case opened nearby while the user's own (other model) earbuds are connected.
        val beacon = FdeeBeacon.parse("01 01 01 03 00 01 55 0C E4".hexToBytes())!!
        val state = BudsState(link = LinkState.CONNECTED, battery = BatteryState(70, 70, 60, 40, false, false, false), profileId = "freebuds-pro-4")
        val model = popupModel(beacon, profile)
        assertEquals(model, model.withState(state))
        assertFalse(model.withState(state).connected)
    }

    @Test
    fun dropsBackWhenTheEarbudsDisconnect() {
        val beacon = FdeeBeacon.parse("01 01 01 03 00 01 55 0C E4".hexToBytes())!!
        val live = BudsState(link = LinkState.CONNECTED, battery = BatteryState(70, 70, 60, 40, false, false, false), profileId = "freebuds-6")
        val connected = popupModel(beacon, profile).withState(live)
        assertTrue(connected.connected)
        // The controller keeps the profile on a drop; a new connect starts from the generic profile.
        for (state in listOf(live.copy(link = LinkState.DISCONNECTED), live.copy(link = LinkState.TAKEN_OVER), BudsState(link = LinkState.CONNECTING))) {
            val dropped = connected.withState(state)
            assertFalse(dropped.connected)
            assertEquals(connected.batteries, dropped.batteries)
        }
    }
}
