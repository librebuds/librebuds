// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EventLogTest {
    @Test
    fun masksAllButTheLastTwoBytesOfAMac() {
        assertEquals("connect **:**:**:**:55:66", EventLog.maskMacs("connect 11:22:33:44:55:66"))
        assertEquals("**:**:**:**:ee:ff and **:**:**:**:0B:0C", EventLog.maskMacs("aa-bb-cc-dd-ee-ff and 01:02:03:04:0B:0C"))
    }

    @Test
    fun leavesTextWithoutAMacAlone() {
        assertEquals("session closed (clean), state DISCONNECTED", EventLog.maskMacs("session closed (clean), state DISCONNECTED"))
        // Hex frame bytes are space-separated, not an address.
        assertEquals("5A 00 05 00 2B 2A", EventLog.maskMacs("5A 00 05 00 2B 2A"))
        assertEquals("none", EventLog.maskMac(null))
    }

    @Test
    fun recordsMaskedJsonLines() {
        var now = 5L
        val log = EventLog(capacity = 10, clock = { now++ })
        log.record("BudsService", "start 11:22:33:44:55:66: \"quoted\"")
        assertEquals(
            "{\"type\":\"event\",\"ts\":5,\"tag\":\"BudsService\",\"msg\":\"start **:**:**:**:55:66: \\\"quoted\\\"\"}",
            log.toJsonl(),
        )
        assertFalse(log.toJsonl().contains("11:22:33"))
    }

    @Test
    fun dropsOldestBeyondCapacity() {
        val log = EventLog(capacity = 2, clock = { 0L })
        log.record("t", "one")
        log.record("t", "two")
        log.record("t", "three")
        assertEquals(2, log.size())
        assertFalse(log.toJsonl().contains("one"))
    }
}
