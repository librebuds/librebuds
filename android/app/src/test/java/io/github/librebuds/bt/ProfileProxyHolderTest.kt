// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.bt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileProxyHolderTest {
    private val a2dp = 2
    private val headset = 1
    private val binds = mutableListOf<Int>()
    private val closed = mutableListOf<Pair<Int, String>>()
    private val holder = ProfileProxyHolder<String>(
        profiles = setOf(a2dp, headset),
        bind = { binds += it; true },
        close = { profile, proxy -> closed += profile to proxy },
    )

    @Test
    fun `acquire binds each profile once however often it is called`() {
        assertTrue(holder.acquire())
        holder.acquire()
        holder.onConnected(a2dp, "a")
        holder.acquire()
        assertEquals(setOf(a2dp, headset), binds.toSet())
        assertEquals(2, binds.size)
    }

    @Test
    fun `answers are kept and read until release closes them`() {
        holder.acquire()
        assertTrue(holder.onConnected(a2dp, "a"))
        assertFalse(holder.complete())
        assertTrue(holder.onConnected(headset, "h"))
        assertTrue(holder.complete())
        assertEquals("a", holder.proxy(a2dp))
        assertTrue(closed.isEmpty())

        holder.release()
        assertEquals(setOf(a2dp to "a", headset to "h"), closed.toSet())
        assertNull(holder.proxy(a2dp))
        assertFalse(holder.complete())
    }

    @Test
    fun `a proxy arriving after release is closed at once`() {
        holder.acquire()
        holder.release()
        assertFalse(holder.onConnected(a2dp, "late"))
        assertEquals(listOf(a2dp to "late"), closed)
        assertNull(holder.proxy(a2dp))
    }

    @Test
    fun `a proxy for a profile never asked for is closed`() {
        holder.acquire()
        assertFalse(holder.onConnected(99, "x"))
        assertEquals(listOf(99 to "x"), closed)
    }

    @Test
    fun `acquire after release binds again`() {
        holder.acquire()
        holder.onConnected(a2dp, "a")
        holder.release()
        binds.clear()
        holder.acquire()
        assertEquals(setOf(a2dp, headset), binds.toSet())
        assertTrue(holder.onConnected(a2dp, "a2"))
        assertEquals("a2", holder.proxy(a2dp))
    }

    @Test
    fun `Bluetooth off drops the proxy without binding again, and its return is kept`() {
        holder.acquire()
        holder.onConnected(a2dp, "a")
        holder.onConnected(headset, "h")
        holder.onDisconnected(a2dp)
        assertNull(holder.proxy(a2dp))
        assertFalse(holder.complete())
        holder.acquire()
        assertEquals(2, binds.size)
        // The system hands the same listener the proxy again once Bluetooth is back.
        assertTrue(holder.onConnected(a2dp, "a"))
        assertTrue(holder.complete())
        assertTrue(closed.isEmpty())
    }

    @Test
    fun `a replaced proxy is closed, the same one delivered again is not`() {
        holder.acquire()
        holder.onConnected(a2dp, "a")
        holder.onConnected(a2dp, "a")
        assertTrue(closed.isEmpty())
        holder.onConnected(a2dp, "b")
        assertEquals(listOf(a2dp to "a"), closed)
        assertSame("b", holder.proxy(a2dp))
    }

    @Test
    fun `a refused bind is tried again on the next acquire`() {
        var allow = false
        val flaky = ProfileProxyHolder<String>(setOf(a2dp), bind = { binds += it; allow }, close = { _, _ -> })
        assertFalse(flaky.acquire())
        allow = true
        assertTrue(flaky.acquire())
        assertEquals(listOf(a2dp, a2dp), binds)
    }
}
