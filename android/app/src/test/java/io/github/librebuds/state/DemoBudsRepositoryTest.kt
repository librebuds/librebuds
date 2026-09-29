// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import io.github.librebuds.protocol.command.AncMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoBudsRepositoryTest {
    @Test
    fun startsConnectedWithBatteryAndAnc() {
        val state = DemoBudsRepository(clock = { 1000L }).state.value
        assertTrue(state.isConnected)
        assertEquals(100, state.battery?.left)
        assertEquals(AncMode.OFF, state.anc?.mode)
    }

    @Test
    fun setAncUpdatesStateAfterDelay() = runTest {
        val repo = DemoBudsRepository(clock = { 2000L }, applyDelayMillis = 800)
        val result = repo.setAnc(AncMode.CANCELLATION)
        assertEquals(AncMode.CANCELLATION, result.getOrThrow().mode)
        assertEquals(AncMode.CANCELLATION, repo.state.value.anc?.mode)
        assertEquals(2000L, repo.state.value.updatedAtMillis)
    }
}
