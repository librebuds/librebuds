// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.bt

import org.junit.Assert.assertEquals
import org.junit.Test

class CoalescedReaderTest {
    private val background = ArrayDeque<Runnable>()
    private val main = ArrayDeque<Runnable>()
    private var reads = 0
    private val delivered = mutableListOf<Int>()
    private val reader = CoalescedReader(
        background = { background.addLast(it) },
        main = { main.addLast(it) },
        read = { ++reads },
        deliver = { delivered += it },
    )

    private fun runBackground() = generateSequence { background.removeFirstOrNull() }.forEach { it.run() }
    private fun runMain() = generateSequence { main.removeFirstOrNull() }.forEach { it.run() }

    @Test
    fun `a burst of requests makes one read and one delivery`() {
        repeat(12) { reader.request() }
        assertEquals(1, background.size)
        runBackground()
        runMain()
        assertEquals(1, reads)
        assertEquals(listOf(1), delivered)
    }

    @Test
    fun `a request after the read started gets one more read`() {
        reader.request()
        background.removeFirst().run() // reads 1
        reader.request()
        reader.request()
        runMain()
        assertEquals(listOf(1), delivered)
        runBackground()
        runMain()
        assertEquals(listOf(1, 2), delivered)
        assertEquals(2, reads)
    }

    @Test
    fun `a request after the delivery starts a new read`() {
        reader.request()
        runBackground()
        runMain()
        reader.request()
        runBackground()
        runMain()
        assertEquals(listOf(1, 2), delivered)
    }

    @Test
    fun `no request, no read`() {
        runBackground()
        runMain()
        assertEquals(0, reads)
    }
}
