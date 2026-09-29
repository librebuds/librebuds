// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.command.HostAction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun seedsSettingsAndTwoHosts() {
        val state = DemoBudsRepository(clock = { 1000L }).state.value
        assertEquals(true, state.settings.wearDetection)
        assertEquals(Gesture.entries.toSet(), state.settings.gestures.keys)
        assertEquals(1, state.settings.equalizer?.active)
        assertEquals(true, state.multipointEnabled)
        assertEquals(listOf("Phone", "Laptop"), state.hosts.map { it.name })
        assertEquals(listOf("11:22:33:44:55:66", "11:22:33:44:55:77"), state.hosts.map { it.mac })
        assertTrue("multipoint" in state.capabilities)
    }

    @Test
    fun settingChangesUpdateTheInMemoryState() = runTest {
        val repo = DemoBudsRepository(clock = { 3000L }, applyDelayMillis = 800)
        assertTrue(repo.apply(SettingChange.LowLatencyChange(true)).isSuccess)
        assertEquals(true, repo.state.value.settings.lowLatency)
        assertTrue(repo.apply(SettingChange.HostCommand(HostAction.DISCONNECT, "11:22:33:44:55:77")).isSuccess)
        assertFalse(repo.state.value.hosts[1].connected)
        assertEquals(2, repo.refreshHosts().getOrThrow().size)
    }
}
