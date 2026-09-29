// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.bt.LinkFactory
import io.github.librebuds.bt.isAudioConnected
import io.github.librebuds.companion.Presence
import io.github.librebuds.companion.trackPresence
import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.service.shouldLaunchConnect
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkError
import io.github.librebuds.state.LinkState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        advanceTimeBy(60_000)
        assertEquals(LinkState.TAKEN_OVER, c.state.value.link)
        assertEquals(1, links.size)
    }

    // Final review I1: after a process start AclTracker has seen no ACL broadcast. A companion
    // presence event alone must be enough for a remote drop to count as TAKEN_OVER (and not as
    // DISCONNECTED, which would let a later START reconnect by itself).
    @Test
    fun dropWithAudioSeededOnlyByPresenceIsTakenOver() = runTest {
        trackPresence(Presence.APPEARED, "AA")
        try {
            val links = mutableListOf<FakeLink>()
            val c = BudsController(
                linkFactory = LinkFactory { FakeEarbuds().link().also { links += it } },
                registry = registry,
                scope = backgroundScope,
                isAudioConnected = ::isAudioConnected,
            )
            c.connect("AA", "x")
            links.single().endOfStream()
            runCurrent()
            assertEquals(LinkState.TAKEN_OVER, c.state.value.link)
        } finally {
            trackPresence(Presence.DISAPPEARED, "AA")
        }
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

    // Final review I2: earbuds that stop answering make the session give up on its own. That is not
    // another device taking the link, so even with audio up the state is DISCONNECTED with a sticky
    // NO_REPLY, which the next connect clears.
    @Test
    fun unresponsiveEarbudsWithAudioAreDisconnectedWithNoReply() = runTest {
        val earbuds = FakeEarbuds()
        val links = mutableListOf<FakeLink>()
        val c = controller(earbuds, audio = true, links = links)
        c.connect("AA", "x")
        advanceUntilIdle()
        earbuds.silent = true
        repeat(2) { c.refresh() }
        runCurrent()
        assertEquals(LinkState.DISCONNECTED, c.state.value.link)
        assertEquals(LinkError.NO_REPLY, c.state.value.lastError)
        assertEquals(1, links.size)

        earbuds.silent = false
        c.connect("AA", "x")
        advanceUntilIdle()
        assertEquals(LinkState.CONNECTED, c.state.value.link)
        assertEquals(null, c.state.value.lastError)
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

    // Regression tests for fix round 1 (task-3-findings.md). Each mirrors a reviewer probe that
    // was run against 6a8fb77 to confirm the bug empirically before these assertions were written.

    // Finding 1: the packets collector for a session never completes on its own (it collects a
    // SharedFlow that has no terminal state), so it must be cancelled explicitly whenever its
    // session ends. Reconnect 5 times (each ended by a link drop while audio stays connected, so
    // every reconnect goes through takeOver()) and assert nothing is left running in the
    // background scope. Pre-fix, the reviewer's probe measured 5 active children for 5 drops.
    @Test
    fun leakedCollectorsAreCancelled() = runTest {
        val links = mutableListOf<FakeLink>()
        val earbuds = FakeEarbuds()
        val c = controller(earbuds, audio = true, links = links)
        repeat(5) {
            if (it == 0) c.connect("AA", "x") else c.takeOver()
            links.last().endOfStream()
            runCurrent()
        }
        val activeChildren = backgroundScope.coroutineContext[Job]!!.children.count { it.isActive }
        assertEquals(0, activeChildren)
    }

    // Finding 2: a link that drops during connect() (here: every write is immediately followed by
    // endOfStream(), so the very first request never gets a reply) must not leave the state
    // CONNECTED. DeviceSession.close()/the reader's own EOF path completes `closed` synchronously,
    // before connect() resumes from the failed request, so connect() must re-check
    // `!current.closed.isCompleted` there and bail rather than publish CONNECTED over whatever
    // onClosed() already set.
    @Test
    fun dropDuringConnectEndsDisconnected() = runTest {
        val c = BudsController(LinkFactory { FakeLink { endOfStream() } }, registry, backgroundScope)
        c.connect("AA", "x")
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(LinkState.DISCONNECTED, c.state.value.link)
        assertTrue(c.setAnc(AncMode.OFF).exceptionOrNull() is NotConnectedException)
    }

    // Finding 3: DeviceSession.send() does not wrap write failures in a Result, so a write to an
    // already-closed link throws out of setAnc() instead of failing it. Close the link directly
    // (bypassing the session, the way a lower Bluetooth layer would) and confirm setAnc() returns
    // Result.failure instead of throwing.
    @Test
    fun setAncOnDeadLinkFails() = runTest {
        val links = mutableListOf<FakeLink>()
        val c = controller(FakeEarbuds(), links = links)
        c.connect("AA", "x")
        advanceUntilIdle()
        links.single().close()
        val result = c.setAnc(AncMode.AWARENESS)
        assertTrue(result.exceptionOrNull() is SessionClosedException)
    }

    // Finding 4: disconnect() must win over a connect() that is already in flight. The earbuds
    // stay silent (no replies) so connect() is parked inside its first request() when disconnect()
    // runs; connect() must notice the generation bump when it resumes and leave the DISCONNECTED
    // state disconnect() set, instead of overwriting it with CONNECTED once its request eventually
    // fails.
    @Test
    fun disconnectDuringConnectWins() = runTest {
        val earbuds = FakeEarbuds(silent = true)
        val links = mutableListOf<FakeLink>()
        val c = controller(earbuds, links = links)
        val job = backgroundScope.launch { c.connect("AA", "x") }
        runCurrent()
        c.disconnect()
        earbuds.silent = false
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(LinkState.DISCONNECTED, c.state.value.link)
        assertTrue(job.isCompleted)
    }

    @Test
    fun restoredTakenOverStateBlocksAutoConnect() = runTest {
        val restored = BudsState(link = LinkState.TAKEN_OVER, address = "AA:BB:CC:DD:EE:FF", name = "x", battery = null)
        val c = BudsController(LinkFactory { FakeEarbuds().link() }, registry, backgroundScope, initial = restored)
        assertEquals(LinkState.TAKEN_OVER, c.state.value.link)
        assertFalse(shouldLaunchConnect(c.state.value, "AA:BB:CC:DD:EE:FF"))
        assertFalse(shouldLaunchConnect(c.state.value, "aa:bb:cc:dd:ee:ff"))
        assertTrue(shouldLaunchConnect(c.state.value, "11:22:33:44:55:66"))
    }

    @Test
    fun takeOverFromRestoredStateReconnects() = runTest {
        val restored = BudsState(link = LinkState.TAKEN_OVER, address = "AA", name = "x")
        val c = BudsController(LinkFactory { FakeEarbuds().link() }, registry, backgroundScope, initial = restored)
        assertTrue(c.takeOver().isSuccess)
        assertEquals(LinkState.CONNECTED, c.state.value.link)
    }

    @Test
    fun openFailureWithAudioUpIsTakenOver() = runTest {
        val c = BudsController(LinkFactory { throw IOException("busy") }, registry, backgroundScope, isAudioConnected = { it == "AA" })
        c.connect("AA", "x")
        assertEquals(LinkState.TAKEN_OVER, c.state.value.link)
        assertFalse(shouldLaunchConnect(c.state.value, "AA"))
        c.connect("BB", "y")
        assertEquals(LinkState.DISCONNECTED, c.state.value.link)
    }

    @Test
    fun reconnectKeepsLastKnownValuesUntilFreshOnesArrive() = runTest {
        val battery = BatteryState(50, 50, 50, 50, false, false, false)
        val restored = BudsState(address = "AA", battery = battery, anc = AncState(1, 3), updatedAtMillis = 42)
        val c = BudsController(LinkFactory { throw IOException("page timeout") }, registry, backgroundScope, initial = restored)
        c.connect("AA", "x")
        assertEquals(LinkState.DISCONNECTED, c.state.value.link)
        assertEquals(battery, c.state.value.battery)
        assertEquals(42L, c.state.value.updatedAtMillis)
        c.connect("BB", "y")
        assertEquals(null, c.state.value.battery)
    }

    // Task 6 review #1: carried-over values are cleared when the connected session cannot read them.
    @Test
    fun failedReadsOnAConnectedSessionClearCarriedOverValues() = runTest {
        val battery = BatteryState(50, 50, 50, 50, false, false, false)
        val restored = BudsState(address = "AA", battery = battery, anc = AncState(1, 3), updatedAtMillis = 42)
        val earbuds = FakeEarbuds(ignoreReads = setOf("01/08", "2B/2A"))
        val c = BudsController(LinkFactory { earbuds.link() }, registry, backgroundScope, initial = restored)
        c.connect("AA", "x")
        assertEquals(LinkState.CONNECTED, c.state.value.link)
        assertEquals(null, c.state.value.battery)
        assertEquals(null, c.state.value.anc)
    }
}
