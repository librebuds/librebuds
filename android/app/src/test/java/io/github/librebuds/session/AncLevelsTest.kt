// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.profile.ProfileRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class AncLevelsTest {
    private val dynamic = ProfileRegistry.fromJson(listOf("""{"id":"d","name":"D","capabilities":{"anc":{"cancellationLevels":[3]}}}""")).profiles.single()
    private val plain = ProfileRegistry.GENERIC

    @Test
    fun keepsLevelWhenModeUnchanged() {
        assertEquals(2, levelFor(AncMode.CANCELLATION, AncState(modeCode = 1, level = 2), dynamic))
    }

    @Test
    fun switchingModeLetsTheEarbudsPickTheLevel() {
        assertEquals(0xFF, levelFor(AncMode.CANCELLATION, AncState(modeCode = 0, level = 0), dynamic))
        assertEquals(0xFF, levelFor(AncMode.CANCELLATION, AncState(modeCode = 0, level = 0), plain))
        assertEquals(0xFF, levelFor(AncMode.AWARENESS, AncState(modeCode = 1, level = 3), dynamic))
        assertEquals(0xFF, levelFor(AncMode.OFF, AncState(modeCode = 1, level = 3), dynamic))
        assertEquals(0xFF, levelFor(AncMode.OFF, null, dynamic))
    }
}
