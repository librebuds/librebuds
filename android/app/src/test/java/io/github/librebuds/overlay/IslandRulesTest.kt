// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.overlay

import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.state.LinkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandRulesTest {
    private fun show(previous: LinkState?, current: LinkState, enabled: Boolean = true, overlay: Boolean = true) =
        islandShouldShow(previous, current, enabled, overlay)

    @Test
    fun connectingToConnectedShows() {
        assertTrue(show(LinkState.CONNECTING, LinkState.CONNECTED))
    }

    @Test
    fun disconnectedToConnectedShows() {
        assertTrue(show(LinkState.DISCONNECTED, LinkState.CONNECTED))
    }

    @Test
    fun takenOverToConnectedShows() {
        assertTrue(show(LinkState.TAKEN_OVER, LinkState.CONNECTED))
    }

    @Test
    fun stayingConnectedDoesNotShowAgain() {
        assertFalse(show(LinkState.CONNECTED, LinkState.CONNECTED))
    }

    @Test
    fun alreadyConnectedWhenFirstObservedDoesNotShow() {
        assertFalse(show(null, LinkState.CONNECTED))
    }

    @Test
    fun otherTargetsDoNotShow() {
        assertFalse(show(LinkState.CONNECTED, LinkState.DISCONNECTED))
        assertFalse(show(LinkState.DISCONNECTED, LinkState.CONNECTING))
        assertFalse(show(LinkState.CONNECTED, LinkState.TAKEN_OVER))
    }

    @Test
    fun preferenceOffDoesNotShow() {
        assertFalse(show(LinkState.CONNECTING, LinkState.CONNECTED, enabled = false))
    }

    @Test
    fun missingOverlayPermissionDoesNotShow() {
        assertFalse(show(LinkState.CONNECTING, LinkState.CONNECTED, overlay = false))
    }

    private fun battery(left: Int?, right: Int?) = BatteryState(null, left, right, 90, null, null, null)

    @Test
    fun batteryLevelIsLowerEarbud() {
        assertEquals(40, islandBatteryLevel(battery(80, 40)))
    }

    @Test
    fun batteryLevelUsesTheKnownEarbud() {
        assertEquals(70, islandBatteryLevel(battery(null, 70)))
        assertEquals(55, islandBatteryLevel(battery(55, null)))
    }

    @Test
    fun batteryLevelIsZeroWhenUnknown() {
        assertEquals(0, islandBatteryLevel(battery(null, null)))
        assertEquals(0, islandBatteryLevel(null))
    }
}
