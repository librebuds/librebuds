// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.bt.LinkFactory
import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.state.LinkState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BudsControllerTest {
    private val registry = ProfileRegistry.fromJson(
        listOf(
            """{"id":"freebuds-6","name":"FreeBuds 6","match":{"sku":["BTFT0020"]},"capabilities":{"battery":{},"anc":{"cancellationLevels":[3]}}}""",
        ),
    )

    private fun TestScope.controller(earbuds: FakeEarbuds, audio: Boolean = false, links: MutableList<FakeLink> = mutableListOf()) =
        BudsController(
            linkFactory = LinkFactory { earbuds.link().also { links += it } },
            registry = registry,
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
            isAudioConnected = { audio },
            settleMillis = 1500,
        )

    @Test
    fun connectReadsInfoBatteryAndAnc() = runTest {
        val c = controller(FakeEarbuds())
        c.connect("AA:BB:CC:DD:EE:FF", "HUAWEI FreeBuds 6")
        advanceUntilIdle()
        val s = c.state.value
        assertEquals(LinkState.CONNECTED, s.link)
        assertEquals("freebuds-6", s.profileId)
        assertEquals(100, s.battery?.left)
        assertEquals(AncMode.OFF, s.anc?.mode)
        assertEquals("FW 1.0.0.100", s.device.firmware)
        assertEquals("FreeBuds 6", s.device.model)
    }

    @Test
    fun unknownSkuUsesGenericProfile() = runTest {
        val c = controller(FakeEarbuds(sku = "UNKNOWN1"))
        c.connect("AA:BB:CC:DD:EE:FF", "Renamed buds")
        advanceUntilIdle()
        assertEquals("generic", c.state.value.profileId)
        assertTrue("anc" in c.state.value.capabilities)
    }

    @Test
    fun setAncConfirmedByReRead() = runTest {
        val earbuds = FakeEarbuds()
        val c = controller(earbuds)
        c.connect("AA", "x")
        val result = c.setAnc(AncMode.CANCELLATION)
        assertEquals(AncMode.CANCELLATION, result.getOrThrow().mode)
        assertEquals(3, earbuds.ancLevel)
        assertEquals(AncMode.CANCELLATION, c.state.value.anc?.mode)
    }

    @Test
    fun rejectedWriteFails() = runTest {
        val earbuds = FakeEarbuds(acceptModes = setOf(0))
        val c = controller(earbuds)
        c.connect("AA", "x")
        val result = c.setAnc(AncMode.AWARENESS)
        assertTrue(result.exceptionOrNull() is AncRejectedException)
        assertEquals(AncMode.OFF, c.state.value.anc?.mode)
    }

    // Uses runCurrent() rather than advanceUntilIdle(): the trigger below is synchronous from the
    // test body, so only background-scope work (readLoop -> collector) is queued afterward, and
    // advanceUntilIdle() stops once no foreground work remains (see TestScope.backgroundScope KDoc).
    @Test
    fun pushUpdatesState() = runTest {
        val earbuds = FakeEarbuds()
        val links = mutableListOf<FakeLink>()
        val c = controller(earbuds, links = links)
        c.connect("AA", "x")
        earbuds.ancMode = 2
        links.single().deliver(earbuds.ancPush())
        runCurrent()
        assertEquals(AncMode.AWARENESS, c.state.value.anc?.mode)
    }

    @Test
    fun connectFailureLeavesDisconnected() = runTest {
        val c = BudsController(LinkFactory { throw IOException("page timeout") }, registry, backgroundScope)
        c.connect("AA", "x")
        assertEquals(LinkState.DISCONNECTED, c.state.value.link)
    }

    @Test
    fun dropWhileAudioConnectedIsTakenOver() = runTest {
        val links = mutableListOf<FakeLink>()
        val c = controller(FakeEarbuds(), audio = true, links = links)
        c.connect("AA", "x")
        links.single().endOfStream()
        runCurrent()
        assertEquals(LinkState.TAKEN_OVER, c.state.value.link)
        assertEquals(1, links.size)
    }

    @Test
    fun dropWithoutAudioIsDisconnected() = runTest {
        val links = mutableListOf<FakeLink>()
        val c = controller(FakeEarbuds(), audio = false, links = links)
        c.connect("AA", "x")
        links.single().endOfStream()
        runCurrent()
        assertEquals(LinkState.DISCONNECTED, c.state.value.link)
    }

    @Test
    fun takeOverReconnects() = runTest {
        val links = mutableListOf<FakeLink>()
        val c = controller(FakeEarbuds(), audio = true, links = links)
        c.connect("AA", "x")
        links.single().endOfStream()
        runCurrent()
        c.takeOver().getOrThrow()
        advanceUntilIdle()
        assertEquals(LinkState.CONNECTED, c.state.value.link)
        assertEquals(2, links.size)
    }

    @Test
    fun setAncWithoutConnectionFails() = runTest {
        val c = controller(FakeEarbuds())
        assertTrue(c.setAnc(AncMode.OFF).exceptionOrNull() is NotConnectedException)
    }
}
