// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.ui.model.Battery
import io.github.librebuds.ui.model.BatteryComponent
import io.github.librebuds.ui.model.BatteryStatus
import io.github.librebuds.ui.model.NoiseControlMode
import io.github.librebuds.ui.model.toUiBatteries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiModelsTest {
    @Test
    fun mapsBatteryStateToUiBatteries() {
        val ui = BatteryState(74, 100, 84, 74, false, true, null).toUiBatteries()
        assertEquals(
            listOf(
                Battery(BatteryComponent.LEFT, 100, BatteryStatus.NOT_CHARGING),
                Battery(BatteryComponent.RIGHT, 84, BatteryStatus.CHARGING),
                Battery(BatteryComponent.CASE, 74, BatteryStatus.NOT_CHARGING),
            ),
            ui,
        )
    }

    @Test
    fun skipsMissingLevels() {
        assertEquals(emptyList<Battery>(), BatteryState(50, null, null, null, null, null, null).toUiBatteries())
        assertEquals(emptyList<Battery>(), (null as BatteryState?).toUiBatteries())
    }

    @Test
    fun mapsAncStateToNoiseMode() {
        assertEquals(NoiseControlMode.AWARENESS, NoiseControlMode.of(AncState(modeCode = 2, level = 2)))
        assertEquals(NoiseControlMode.OFF, NoiseControlMode.of(AncState(modeCode = 0, level = 3)))
        assertEquals(AncMode.CANCELLATION, NoiseControlMode.NOISE_CANCELLATION.anc)
    }

    @Test
    fun unknownOrMissingModeIsNull() {
        assertNull(NoiseControlMode.of(AncState(modeCode = 7, level = 0)))
        assertNull(NoiseControlMode.of(null))
    }
}
