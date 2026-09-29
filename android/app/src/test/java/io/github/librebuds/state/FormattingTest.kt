// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import io.github.librebuds.protocol.command.BatteryState
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test
    fun summarizesAllLevels() {
        val battery = BatteryState(74, 100, 84, 74, false, false, true)
        assertEquals("L 100% · R 84% · Case 74% (charging)", batterySummary(battery))
    }

    @Test
    fun usesPlaceholdersForMissingValues() {
        assertEquals("L -- · R -- · Case --", batterySummary(BatteryState(50, null, null, null, null, null, null)))
        assertEquals("L -- · R -- · Case --", batterySummary(null))
    }
}
