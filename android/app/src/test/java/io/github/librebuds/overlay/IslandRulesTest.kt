// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.overlay

import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.state.LinkState
import io.github.librebuds.ui.model.Battery
import io.github.librebuds.ui.model.BatteryComponent
import io.github.librebuds.ui.model.BatteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandRulesTest {
    private fun show(previous: LinkState?, current: LinkState, enabled: Boolean = true, overlay: Boolean = true, popup: Boolean = false) =
        islandShouldShow(previous, current, enabled, overlay, popupShowing = popup)

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

    @Test
    fun popupForTheseEarbudsOnScreenDoesNotShow() {
        // The case-open popup already reports the connection; two overlays at once would be noise.
        assertFalse(show(LinkState.CONNECTING, LinkState.CONNECTED, popup = true))
        assertTrue(show(LinkState.CONNECTING, LinkState.CONNECTED, popup = false))
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

    @Test
    fun batteryPartsAreLeftRightCaseWithCharging() {
        val batteries = listOf(
            Battery(BatteryComponent.CASE, 48, BatteryStatus.NOT_CHARGING),
            Battery(BatteryComponent.RIGHT, 80, BatteryStatus.NOT_CHARGING),
            Battery(BatteryComponent.LEFT, 100, BatteryStatus.CHARGING),
        )
        assertEquals(
            listOf(
                IslandBatteryPart(BatteryComponent.LEFT, 100, charging = true),
                IslandBatteryPart(BatteryComponent.RIGHT, 80, charging = false),
                IslandBatteryPart(BatteryComponent.CASE, 48, charging = false),
            ),
            islandBatteryParts(batteries),
        )
    }

    @Test
    fun batteryPartsLeaveOutUnreportedComponents() {
        val batteries = listOf(
            Battery(BatteryComponent.LEFT, 60, BatteryStatus.DISCONNECTED),
            Battery(BatteryComponent.CASE, 30, BatteryStatus.CHARGING),
        )
        assertEquals(listOf(IslandBatteryPart(BatteryComponent.CASE, 30, charging = true)), islandBatteryParts(batteries))
        assertEquals(emptyList<IslandBatteryPart>(), islandBatteryParts(emptyList()))
    }
}
