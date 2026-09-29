// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.bt.LinkFactory
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.command.HostAction
import io.github.librebuds.protocol.frame.FrameReassembler
import io.github.librebuds.protocol.frame.RxEvent
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.state.SettingChange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BudsControllerSettingsTest {
    private val registry = ProfileRegistry.fromJson(
        listOf(
            """{"id":"settings","name":"Settings buds","match":{"sku":["BTFT0020"]},"capabilities":{"battery":{},"anc":{"cancellationLevels":[3]},""" +
                """"wear":{},"gestures":{"doubleTap":{},"swipe":{}},"equalizer":{},"lowLatency":{}}}""",
            """{"id":"multi","name":"Multipoint buds","match":{"sku":["BTFT0030"]},"capabilities":{"battery":{},"multipoint":{}}}""",
        ),
    )

    private fun TestScope.controller(earbuds: FakeEarbuds, links: MutableList<FakeLink> = mutableListOf()) =
        BudsController(
            linkFactory = LinkFactory { earbuds.link().also { links += it } },
            registry = registry,
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
            settleMillis = 1500,
        )

    private fun twoHosts() = mutableListOf(
        FakeHost(mac = "11:22:33:44:55:66", name = "Phone", state = 9, preferred = true),
        FakeHost(mac = "AA:BB:CC:00:11:22", name = "Laptop", state = 1),
    )

    private fun FakeLink.sentIds(): List<String> {
        val reassembler = FrameReassembler()
        return written.flatMap { reassembler.feed(it) }.filterIsInstance<RxEvent.Payload>()
            .mapNotNull { Packet.fromPayload(it.bytes)?.id?.toString() }
    }

    @Test
    fun connectReadsSupportedSettings() = runTest {
        val earbuds = FakeEarbuds(wear = true, equalizerPreset = 2, lowLatency = true)
        earbuds.gestures.getValue(Gesture.SWIPE).apply { left = 0; right = 0 }
        val c = controller(earbuds)
        c.connect("AA", "x")
        advanceUntilIdle()
        val settings = c.state.value.settings
        assertEquals(true, settings.wearDetection)
        assertEquals(setOf(Gesture.DOUBLE_TAP, Gesture.SWIPE), settings.gestures.keys)
        assertEquals(1, settings.gestures.getValue(Gesture.DOUBLE_TAP).left)
        assertEquals(0, settings.gestures.getValue(Gesture.SWIPE).left)
        assertEquals(2, settings.equalizer?.active)
        assertEquals(listOf(1, 2, 3), settings.equalizer?.available)
        assertEquals(true, settings.lowLatency)
        assertNull(settings.soundQuality)
        assertNull(settings.language)
    }

    @Test
    fun gestureChangeConfirmedByReadBack() = runTest {
        val earbuds = FakeEarbuds()
        val c = controller(earbuds)
        c.connect("AA", "x")
        val result = c.apply(SettingChange.GestureChange(Gesture.DOUBLE_TAP, left = 2, right = -1, inCall = null))
        assertTrue(result.isSuccess)
        assertEquals(2, earbuds.gestures.getValue(Gesture.DOUBLE_TAP).left)
        val setting = c.state.value.settings.gestures.getValue(Gesture.DOUBLE_TAP)
        assertEquals(2, setting.left)
        assertEquals(-1, setting.right)
    }

    @Test
    fun ignoredGestureWriteIsRejected() = runTest {
        val earbuds = FakeEarbuds(ignoreWrites = setOf("01/1F"))
        val c = controller(earbuds)
        c.connect("AA", "x")
        val before = c.state.value.settings.gestures.getValue(Gesture.DOUBLE_TAP)
        val result = c.apply(SettingChange.GestureChange(Gesture.DOUBLE_TAP, left = 2, right = 2, inCall = null))
        assertTrue(result.exceptionOrNull() is AncRejectedException)
        assertEquals(before, c.state.value.settings.gestures.getValue(Gesture.DOUBLE_TAP))
        assertEquals(1, c.state.value.settings.gestures.getValue(Gesture.DOUBLE_TAP).left)
    }

    @Test
    fun hostListCollected() = runTest {
        val c = controller(FakeEarbuds(sku = "BTFT0030", hosts = twoHosts()))
        c.connect("AA", "x")
        advanceUntilIdle()
        val hosts = c.state.value.hosts
        assertEquals(listOf("11:22:33:44:55:66", "AA:BB:CC:00:11:22"), hosts.map { it.mac })
        assertEquals(listOf("Phone", "Laptop"), hosts.map { it.name })
        assertTrue(hosts[0].preferred)
        assertTrue(hosts[0].playing)
        assertEquals(true, c.state.value.multipointEnabled)
    }

    @Test
    fun partialHostListAfterTimeout() = runTest {
        val c = controller(FakeEarbuds(sku = "BTFT0030", hosts = twoHosts(), hostRowLimit = 1))
        c.connect("AA", "x")
        assertTrue(testScheduler.currentTime >= 3000)
        assertEquals(1, c.state.value.hosts.size)
    }

    @Test
    fun changePushRefreshesHosts() = runTest {
        val earbuds = FakeEarbuds(sku = "BTFT0030", hosts = twoHosts())
        val links = mutableListOf<FakeLink>()
        val c = controller(earbuds, links)
        c.connect("AA", "x")
        val link = links.single()
        assertEquals(1, link.sentIds().count { it == "2B/31" })
        earbuds.hosts[1].state = 0
        link.deliver(earbuds.hostChangePush())
        runCurrent()
        assertEquals(2, link.sentIds().count { it == "2B/31" })
        assertFalse(c.state.value.hosts[1].connected)
    }

    @Test
    fun disconnectHostCommandVerified() = runTest {
        val earbuds = FakeEarbuds(sku = "BTFT0030", hosts = twoHosts())
        val c = controller(earbuds)
        c.connect("AA", "x")
        val result = c.apply(SettingChange.HostCommand(HostAction.DISCONNECT, "AA:BB:CC:00:11:22"))
        assertTrue(result.isSuccess)
        assertEquals(0, earbuds.hosts[1].state)
        assertFalse(c.state.value.hosts.single { it.mac == "AA:BB:CC:00:11:22" }.connected)
    }
}
