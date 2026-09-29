// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.command.Anc
import io.github.librebuds.protocol.command.Battery
import io.github.librebuds.protocol.util.hexToBytes
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSessionTest {
    private val ancState = "5A 00 07 00 2B 2A 01 02 00 00 15 31"
    private val batteryPush = "5A 00 10 00 01 27 01 01 5A 02 03 64 5A 30 03 03 00 00 01 E7 9E"

    @Test
    fun requestGetsMatchingReply() = runTest {
        val earbuds = FakeEarbuds()
        val session = DeviceSession(earbuds.link(), backgroundScope)
        val reply = session.request(Battery.request()).getOrThrow()
        assertEquals("01/08", reply.id.toString())
        assertEquals(100, Battery.parse(reply)?.left)
    }

    @Test
    fun pushesArriveWithoutRequest() = runTest {
        val link = FakeLink()
        val session = DeviceSession(link, backgroundScope)
        val first = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { session.packets.first() }
        link.deliver(batteryPush.hexToBytes())
        assertEquals("01/27", first.await().id.toString())
    }

    @Test
    fun gluedReplyAndPushBothDelivered() = runTest {
        val link = FakeLink { deliver("$ancState $batteryPush".hexToBytes()) }
        val session = DeviceSession(link, backgroundScope)
        val seen = mutableListOf<Packet>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { session.packets.collect { seen += it } }
        val reply = session.request(Anc.readRequest()).getOrThrow()
        advanceUntilIdle()
        assertEquals("2B/2A", reply.id.toString())
        assertEquals(listOf("2B/2A", "01/27"), seen.map { it.id.toString() })
    }

    @Test
    fun timesOutAfterRetries() = runTest {
        val link = FakeLink()
        val session = DeviceSession(link, backgroundScope, timeoutMillis = 3000, retries = 2)
        val result = session.request(Battery.request())
        assertTrue(result.exceptionOrNull() is RequestTimeoutException)
        assertEquals(3, link.written.size)
        assertEquals(9000L, testScheduler.currentTime)
    }

    @Test
    fun endOfStreamFailsPendingRequestAndCompletesClosed() = runTest {
        val link = FakeLink { endOfStream() }
        val session = DeviceSession(link, backgroundScope)
        val result = session.request(Battery.request())
        assertTrue(result.exceptionOrNull() is SessionClosedException)
        assertEquals(null, session.closed.await())
    }

    @Test
    fun closesAfterConsecutiveFailures() = runTest {
        val link = FakeLink()
        val session = DeviceSession(link, backgroundScope, timeoutMillis = 100, retries = 0, maxConsecutiveFailures = 3)
        repeat(3) { session.request(Battery.request()) }
        advanceUntilIdle()
        assertTrue(session.closed.isCompleted)
        assertTrue(link.closed)
    }

    @Test
    fun logsBothDirections() = runTest {
        val directions = mutableListOf<String>()
        val session = DeviceSession(FakeEarbuds().link(), backgroundScope, onFrame = { d, _ -> directions += d.name })
        session.request(Battery.request()).getOrThrow()
        assertEquals(listOf("TX", "RX"), directions)
    }

    @Test
    fun closeEndsSessionAndFailsLaterRequests() = runTest {
        val link = FakeLink()
        val session = DeviceSession(link, backgroundScope)
        session.close()
        advanceUntilIdle()
        assertTrue(session.closed.isCompleted)
        assertTrue(link.closed)
        assertTrue(session.request(Battery.request()).exceptionOrNull() is SessionClosedException)
    }
}
