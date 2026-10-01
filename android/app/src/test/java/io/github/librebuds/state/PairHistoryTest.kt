// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairHistoryTest {
    @Test
    fun roundTrips() {
        val history = mapOf("AA:BB:CC:DD:EE:FF" to 1_000L, "11:22:33:44:55:66" to 2_000L)
        assertEquals(history, PairHistoryCodec.decode(PairHistoryCodec.encode(history)))
    }

    @Test
    fun malformedEntriesAreSkipped() {
        assertEquals(mapOf("AA:BB:CC:DD:EE:FF" to 5L), PairHistoryCodec.decode("aa:bb:cc:dd:ee:ff=5;garbage;=7;X=notanumber"))
        assertTrue(PairHistoryCodec.decode(null).isEmpty())
        assertTrue(PairHistoryCodec.decode("").isEmpty())
    }

    @Test
    fun recordUppercasesAndReplaces() {
        val history = PairHistoryCodec.record(mapOf("AA:BB:CC:DD:EE:FF" to 1L), "aa:bb:cc:dd:ee:ff", 9L)
        assertEquals(mapOf("AA:BB:CC:DD:EE:FF" to 9L), history)
    }

    @Test
    fun recordKeepsOnlyTheNewestEntries() {
        var history = emptyMap<String, Long>()
        repeat(PairHistoryCodec.MAX_ENTRIES + 4) { i -> history = PairHistoryCodec.record(history, "00:00:00:00:00:%02X".format(i), i.toLong()) }
        assertEquals(PairHistoryCodec.MAX_ENTRIES, history.size)
        assertTrue("00:00:00:00:00:00" !in history)
        assertTrue("00:00:00:00:00:13" in history)
    }
}
