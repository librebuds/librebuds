// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.bt.LinkFactory
import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.command.HostAction
import io.github.librebuds.protocol.frame.FrameReassembler
import io.github.librebuds.protocol.frame.RxEvent
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.state.LinkState
import io.github.librebuds.state.SettingChange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
            """{"id":"all","name":"All buds","match":{"sku":["BTFT0040"]},"capabilities":{"battery":{},"wear":{},"gestures":{"doubleTap":{}},""" +
                """"equalizer":{},"lowLatency":{},"soundQuality":{},"multipoint":{},"language":{}}}""",
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

    // Final review I3: rows the device pushes on its own (no enumerate sent) still make a list, and a
    // second burst with fewer hosts replaces the first instead of merging with it.
    @Test
    fun unsolicitedHostBurstsReplaceEachOther() = runTest {
        val threeHosts = twoHosts().apply { add(FakeHost(mac = "11:22:33:44:55:77", name = "Tablet", state = 0)) }
        // The toggle read goes unanswered, so the controller never enumerates hosts itself.
        val earbuds = FakeEarbuds(sku = "BTFT0030", hosts = threeHosts, ignoreReads = setOf("2B/2F"))
        val links = mutableListOf<FakeLink>()
        val c = controller(earbuds, links)
        c.connect("AA", "x")
        val link = links.single()
        assertEquals(0, link.sentIds().count { it == "2B/31" })
        assertEquals(emptyList<Any>(), c.state.value.hosts)

        earbuds.hostRowPush().forEach { link.deliver(it) }
        runCurrent()
        assertEquals(listOf("11:22:33:44:55:66", "AA:BB:CC:00:11:22", "11:22:33:44:55:77"), c.state.value.hosts.map { it.mac })

        earbuds.hosts.removeAt(1)
        earbuds.hostRowPush().forEach { link.deliver(it) }
        runCurrent()
        assertEquals(listOf("11:22:33:44:55:66", "11:22:33:44:55:77"), c.state.value.hosts.map { it.mac })
        assertEquals(listOf(0, 1), c.state.value.hosts.map { it.index })
    }

    // M5: a malformed MAC fails the change instead of throwing out of apply(), and sends nothing.
    @Test
    fun malformedMacFailsWithoutSending() = runTest {
        val links = mutableListOf<FakeLink>()
        val c = controller(FakeEarbuds(sku = "BTFT0030", hosts = twoHosts()), links)
        c.connect("AA", "x")
        val sent = links.single().sentIds().size
        assertTrue(c.apply(SettingChange.HostCommand(HostAction.CONNECT, "ZZ")).exceptionOrNull() is IllegalArgumentException)
        assertTrue(c.apply(SettingChange.PreferredHost("11:22:33")).exceptionOrNull() is IllegalArgumentException)
        assertEquals(sent, links.single().sentIds().size)
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

    @Test
    fun silentOptionalReadsKeepSessionAlive() = runTest {
        val earbuds = FakeEarbuds(ignoreReads = setOf("2B/11", "01/20", "2B/4A"))
        val c = controller(earbuds)
        c.connect("AA", "x")
        val s = c.state.value
        assertEquals(LinkState.CONNECTED, s.link)
        assertNull(s.lastError)
        assertTrue("connect took ${testScheduler.currentTime} ms", testScheduler.currentTime <= 4 * 1200)
        assertEquals(setOf("wear", "gestures.doubleTap", "equalizer"), s.settings.unanswered)
        assertNull(s.settings.wearDetection)
        assertEquals(setOf(Gesture.SWIPE), s.settings.gestures.keys)
        assertTrue(c.refresh().isSuccess)
        advanceUntilIdle()
        assertEquals(LinkState.CONNECTED, c.state.value.link)
        val before = testScheduler.currentTime
        assertTrue(c.apply(SettingChange.Wear(false)).exceptionOrNull() is SettingUnavailableException)
        assertEquals(before, testScheduler.currentTime)
    }

    // Final review I4: an answer that arrives after its read timed out makes the setting available again.
    @Test
    fun lateAnswerClearsUnanswered() = runTest {
        val earbuds = FakeEarbuds(wear = true, ignoreReads = setOf("2B/11", "01/20"))
        val links = mutableListOf<FakeLink>()
        val c = controller(earbuds, links)
        c.connect("AA", "x")
        assertEquals(setOf("wear", "gestures.doubleTap"), c.state.value.settings.unanswered)

        val link = links.single()
        link.deliver(Packet(CommandId(0x2B, 0x11), listOf(Tlv.of(1, 1))).toFrame())
        link.deliver(Packet(CommandId(0x01, 0x20), listOf(Tlv.of(1, 2), Tlv.of(2, 1))).toFrame())
        runCurrent()
        val settings = c.state.value.settings
        assertEquals(emptySet<String>(), settings.unanswered)
        assertEquals(true, settings.wearDetection)
        assertEquals(2, settings.gestures.getValue(Gesture.DOUBLE_TAP).left)
        // The write is sent now (its read-back stays silent on this fake, so it is not confirmed).
        assertFalse(c.apply(SettingChange.Wear(false)).exceptionOrNull() is SettingUnavailableException)
        assertFalse(earbuds.wear)
    }

    @Test
    fun emptyHostRefreshKeepsKnownHosts() = runTest {
        val earbuds = FakeEarbuds(sku = "BTFT0030", hosts = twoHosts())
        val c = controller(earbuds)
        c.connect("AA", "x")
        earbuds.hostRowLimit = 0
        val result = c.refreshHosts()
        assertTrue(result.exceptionOrNull() is RequestTimeoutException)
        assertEquals(2, c.state.value.hosts.size)
    }

    @Test
    fun refreshHostsUnsupportedWithoutMultipoint() = runTest {
        val c = controller(FakeEarbuds(hosts = twoHosts()))
        c.connect("AA", "x")
        assertTrue(c.refreshHosts().exceptionOrNull() is UnsupportedOperationException)
    }

    @Test
    fun emptyGestureChangeRejectedUpFront() = runTest {
        val links = mutableListOf<FakeLink>()
        val c = controller(FakeEarbuds(), links)
        c.connect("AA", "x")
        val sent = links.single().written.size
        val result = c.apply(SettingChange.GestureChange(Gesture.DOUBLE_TAP, left = null, right = null, inCall = null))
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(sent, links.single().written.size)
    }

    @Test
    fun autoConnectWithoutReportIsUnverifiableSuccess() = runTest {
        val earbuds = FakeEarbuds(sku = "BTFT0030", hosts = twoHosts(), reportAutoConnect = false)
        val c = controller(earbuds)
        c.connect("AA", "x")
        val result = c.apply(SettingChange.HostCommand(HostAction.DISABLE_AUTO_CONNECT, "AA:BB:CC:00:11:22"))
        assertTrue(result.isSuccess)
        assertNull(c.state.value.hosts[1].autoConnect)
    }

    @Test
    fun ignoredHostCommandRejectedAfterPolling() = runTest {
        val earbuds = FakeEarbuds(sku = "BTFT0030", hosts = twoHosts(), ignoreWrites = setOf("2B/33"))
        val links = mutableListOf<FakeLink>()
        val c = controller(earbuds, links)
        c.connect("AA", "x")
        val start = testScheduler.currentTime
        val result = c.apply(SettingChange.HostCommand(HostAction.DISCONNECT, "AA:BB:CC:00:11:22"))
        assertTrue(result.exceptionOrNull() is AncRejectedException)
        // One enumerate at connect, then three polls.
        assertEquals(4, links.single().sentIds().count { it == "2B/31" })
        assertTrue(testScheduler.currentTime - start >= 1500 + 2 * 2000)
        assertTrue(c.state.value.hosts[1].connected)
    }

    @Test
    fun inEarPushUpdatesState() = runTest {
        val earbuds = FakeEarbuds()
        val links = mutableListOf<FakeLink>()
        val c = controller(earbuds, links)
        c.connect("AA", "x")
        links.single().deliver(earbuds.inEarPush(true))
        runCurrent()
        assertEquals(true, c.state.value.inEar)
    }

    @Test
    fun wearChangeApplied() = runTest {
        val earbuds = FakeEarbuds(wear = true)
        val c = controller(earbuds)
        c.connect("AA", "x")
        assertTrue(c.apply(SettingChange.Wear(false)).isSuccess)
        assertFalse(earbuds.wear)
        assertEquals(false, c.state.value.settings.wearDetection)
    }

    @Test
    fun equalizerPresetApplied() = runTest {
        val earbuds = FakeEarbuds()
        val c = controller(earbuds)
        c.connect("AA", "x")
        assertTrue(c.apply(SettingChange.EqualizerPreset(3)).isSuccess)
        assertEquals(3, earbuds.equalizerPreset)
        assertEquals(3, c.state.value.settings.equalizer?.active)
    }

    @Test
    fun lowLatencyAppliedWithLiveAck() = runTest {
        val earbuds = FakeEarbuds(lowLatencyAckLive = true)
        val c = controller(earbuds)
        c.connect("AA", "x")
        assertTrue(c.apply(SettingChange.LowLatencyChange(true)).isSuccess)
        assertTrue(earbuds.lowLatency)
        assertEquals(true, c.state.value.settings.lowLatency)
    }

    @Test
    fun lowLatencyAppliedWithStatusAck() = runTest {
        val earbuds = FakeEarbuds(lowLatencyAckLive = false)
        val c = controller(earbuds)
        c.connect("AA", "x")
        assertTrue(c.apply(SettingChange.LowLatencyChange(true)).isSuccess)
        assertTrue(earbuds.lowLatency)
        assertEquals(true, c.state.value.settings.lowLatency)
    }

    @Test
    fun soundQualityApplied() = runTest {
        val earbuds = FakeEarbuds(sku = "BTFT0040")
        val c = controller(earbuds)
        c.connect("AA", "x")
        assertEquals(0, c.state.value.settings.soundQuality)
        assertNotNull(c.state.value.settings.language)
        assertTrue(c.apply(SettingChange.SoundQualityChange(1)).isSuccess)
        assertEquals(1, earbuds.soundQuality)
        assertEquals(1, c.state.value.settings.soundQuality)
    }

    @Test
    fun multipointToggleApplied() = runTest {
        val earbuds = FakeEarbuds(sku = "BTFT0030", hosts = twoHosts())
        val c = controller(earbuds)
        c.connect("AA", "x")
        assertTrue(c.apply(SettingChange.MultipointEnabled(false)).isSuccess)
        assertFalse(earbuds.multipoint)
        assertEquals(false, c.state.value.multipointEnabled)
    }

    @Test
    fun preferredHostApplied() = runTest {
        val earbuds = FakeEarbuds(sku = "BTFT0030", hosts = twoHosts())
        val c = controller(earbuds)
        c.connect("AA", "x")
        assertTrue(c.apply(SettingChange.PreferredHost("AA:BB:CC:00:11:22")).isSuccess)
        assertEquals(listOf(false, true), c.state.value.hosts.map { it.preferred })
    }

    @Test
    fun gestureReadBackWaitsForSettle() = runTest {
        val earbuds = FakeEarbuds()
        val c = controller(earbuds)
        c.connect("AA", "x")
        var writeAt = -1L
        var readAt = -1L
        earbuds.onRequest = { id ->
            if (id == "01/1F") writeAt = testScheduler.currentTime
            if (id == "01/20" && writeAt >= 0) readAt = testScheduler.currentTime
        }
        assertTrue(c.apply(SettingChange.GestureChange(Gesture.DOUBLE_TAP, left = 0, right = null, inCall = null)).isSuccess)
        assertTrue("read-back at $readAt, write at $writeAt", writeAt >= 0 && readAt - writeAt >= 1500)
    }
}
