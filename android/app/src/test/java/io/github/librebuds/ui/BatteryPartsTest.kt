// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.ui.model.Battery
import io.github.librebuds.ui.model.BatteryComponent
import io.github.librebuds.ui.model.BatteryPart
import io.github.librebuds.ui.model.BatteryPartUi
import io.github.librebuds.ui.model.BatteryStatus
import io.github.librebuds.ui.model.batteryParts
import io.github.librebuds.ui.model.toUiBatteries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryPartsTest {
    private fun battery(component: Int, level: Int, status: Int = BatteryStatus.NOT_CHARGING) =
        Battery(component, level, status)

    @Test
    fun allThreePartsInLeftRightCaseOrder() {
        val parts = batteryParts(
            listOf(
                battery(BatteryComponent.CASE, 60, BatteryStatus.CHARGING),
                battery(BatteryComponent.RIGHT, 85),
                battery(BatteryComponent.LEFT, 100),
            )
        )
        assertEquals(
            listOf(
                BatteryPartUi(BatteryPart.LEFT, 100, false),
                BatteryPartUi(BatteryPart.RIGHT, 85, false),
                BatteryPartUi(BatteryPart.CASE, 60, true),
            ),
            parts,
        )
        assertFalse(parts.any { it.missing })
    }

    @Test
    fun equalBudLevelsStaySeparate() {
        val parts = batteryParts(listOf(battery(BatteryComponent.LEFT, 80), battery(BatteryComponent.RIGHT, 82)))
        assertEquals(80, parts[0].level)
        assertEquals(82, parts[1].level)
    }

    @Test
    fun missingBudIsKeptAsAnEmptySlot() {
        val parts = batteryParts(listOf(battery(BatteryComponent.LEFT, 40), battery(BatteryComponent.CASE, 70)))
        assertEquals(listOf(BatteryPart.LEFT, BatteryPart.RIGHT, BatteryPart.CASE), parts.map { it.part })
        assertEquals(BatteryPartUi(BatteryPart.RIGHT, null, false), parts[1])
        assertTrue(parts[1].missing)
        assertFalse(parts[0].missing)
    }

    @Test
    fun missingCaseIsKeptAsAnEmptySlot() {
        val parts = batteryParts(listOf(battery(BatteryComponent.LEFT, 40), battery(BatteryComponent.RIGHT, 41)))
        assertEquals(BatteryPartUi(BatteryPart.CASE, null, false), parts[2])
    }

    @Test
    fun disconnectedPartCountsAsMissingAndNotCharging() {
        val parts = batteryParts(listOf(battery(BatteryComponent.LEFT, 55, BatteryStatus.DISCONNECTED)))
        assertEquals(BatteryPartUi(BatteryPart.LEFT, null, false), parts[0])
    }

    @Test
    fun outOfRangeLevelCountsAsMissing() {
        val parts = batteryParts(
            listOf(
                battery(BatteryComponent.LEFT, 255, BatteryStatus.CHARGING),
                battery(BatteryComponent.RIGHT, -1),
                battery(BatteryComponent.CASE, 101),
            )
        )
        assertTrue(parts.all { it.missing && !it.charging })
    }

    @Test
    fun zeroAndFullAreRealLevels() {
        val parts = batteryParts(listOf(battery(BatteryComponent.LEFT, 0), battery(BatteryComponent.RIGHT, 100)))
        assertEquals(0, parts[0].level)
        assertEquals(100, parts[1].level)
    }

    @Test
    fun emptyListGivesThreeMissingParts() {
        val parts = batteryParts(emptyList())
        assertEquals(3, parts.size)
        assertTrue(parts.all { it.missing })
    }

    @Test
    fun fromBatteryStateWithOneBudOut() {
        val parts = batteryParts(BatteryState(70, 90, null, 50, true, null, false).toUiBatteries())
        assertEquals(
            listOf(
                BatteryPartUi(BatteryPart.LEFT, 90, true),
                BatteryPartUi(BatteryPart.RIGHT, null, false),
                BatteryPartUi(BatteryPart.CASE, 50, false),
            ),
            parts,
        )
    }
}
