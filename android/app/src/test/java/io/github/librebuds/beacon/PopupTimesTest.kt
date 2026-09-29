// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import org.junit.Assert.assertEquals
import org.junit.Test

class PopupTimesTest {
    @Test
    fun recordPrunesOldAndFutureEntries() {
        val times = mapOf("old" to 0L, "recent" to 3_000_000L, "future" to 9_000_000L)
        assertEquals(mapOf("recent" to 3_000_000L, "new" to 4_000_000L), PopupTimes.record(times, "new", 4_000_000L))
    }

    @Test
    fun roundTrip() {
        val times = mapOf("000155/0" to 123L, "000199/2" to 456L)
        assertEquals(times, PopupTimes.decode(PopupTimes.encode(times)))
    }

    @Test
    fun missingOrCorruptTextIsEmpty() {
        assertEquals(emptyMap<String, Long>(), PopupTimes.decode(null))
        assertEquals(emptyMap<String, Long>(), PopupTimes.decode("not json"))
    }
}
