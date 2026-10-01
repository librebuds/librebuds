// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.service

import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.DeviceSummary
import io.github.librebuds.state.LinkState
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationContentTest {
    private fun content(state: BudsState) =
        notificationContent(state, appName = "LibreBuds", connected = "Connected", connecting = "Connecting", takenOver = "Other device", waiting = "Waiting")

    private val battery = BatteryState(80, 80, 70, 60, false, false, false)

    @Test
    fun connectedShowsNameModelAndBattery() {
        val state = BudsState(link = LinkState.CONNECTED, name = "My buds", battery = battery, device = DeviceSummary(model = "FreeBuds 5"))
        assertEquals(NotificationContent("My buds", "FreeBuds 5 · L 80% · R 70% · Case 60%"), content(state))
    }

    @Test
    fun connectedBeforeAnythingWasReadSaysConnected() {
        assertEquals(NotificationContent("My buds", "Connected"), content(BudsState(link = LinkState.CONNECTED, name = "My buds")))
    }

    @Test
    fun aModelEqualToTheNameIsNotRepeated() {
        val state = BudsState(link = LinkState.CONNECTED, name = "FreeBuds 5", battery = battery, device = DeviceSummary(model = "FreeBuds 5"))
        assertEquals("L 80% · R 70% · Case 60%", content(state).text)
    }

    @Test
    fun otherStates() {
        assertEquals(NotificationContent("My buds", "Connecting"), content(BudsState(link = LinkState.CONNECTING, name = "My buds")))
        assertEquals(NotificationContent("My buds", "Other device"), content(BudsState(link = LinkState.TAKEN_OVER, name = "My buds")))
        assertEquals(NotificationContent("LibreBuds", "Waiting"), content(BudsState(link = LinkState.DISCONNECTED, name = "My buds")))
    }
}
