// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeScreenTest {
    @Test
    fun connectedRowShowsBatteryNotLastSeen() {
        val text = rowDescriptionText(
            model = "FreeBuds 6",
            connectedLabel = "Connected",
            battery = "L 100% · R 100% · Case 100%",
            lastSeenText = "last seen 14:02",
        )

        assertEquals("FreeBuds 6 · Connected · L 100% · R 100% · Case 100%", text)
    }

    @Test
    fun notConnectedRowShowsLastSeenNotBattery() {
        val text = rowDescriptionText(
            model = "FreeBuds 6",
            connectedLabel = "Not connected",
            battery = null,
            lastSeenText = "last seen 14:02",
        )

        assertEquals("FreeBuds 6 · Not connected · last seen 14:02", text)
    }

    @Test
    fun notConnectedRowWithNothingKnownYetShowsNeither() {
        val text = rowDescriptionText(
            model = "Unknown model",
            connectedLabel = "Not connected",
            battery = null,
            lastSeenText = null,
        )

        assertEquals("Unknown model · Not connected", text)
    }

    @Test
    fun batteryAlwaysWinsOverLastSeenIfBothSomehowPresent() {
        val text = rowDescriptionText(
            model = "FreeBuds 6",
            connectedLabel = "Connected",
            battery = "L 50%",
            lastSeenText = "last seen 14:02",
        )

        assertEquals("FreeBuds 6 · Connected · L 50%", text)
    }
}
