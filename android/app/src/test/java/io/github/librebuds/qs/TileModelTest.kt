// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.qs

import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileModelTest {
    @Test
    fun connectedTileShowsModeAndBattery() {
        val state = BudsState(
            link = LinkState.CONNECTED,
            name = "My buds",
            anc = AncState(modeCode = 1, level = 3),
            battery = BatteryState(80, 90, 70, 50, false, false, false),
        )
        assertEquals(TileModel(active = true, label = "Noise cancellation", subtitle = "L 90% · R 70% · Case 50%"), tileModel(state))
    }

    @Test
    fun disconnectedTileIsInactive() {
        val model = tileModel(BudsState())
        assertFalse(model.active)
        assertEquals("LibreBuds", model.label)
        assertEquals("Not connected", model.subtitle)
    }

    @Test
    fun connectedWithoutAncStateUsesAppName() {
        val model = tileModel(BudsState(link = LinkState.CONNECTED))
        assertTrue(model.active)
        assertEquals("LibreBuds", model.label)
        assertEquals("L -- · R -- · Case --", model.subtitle)
    }
}
