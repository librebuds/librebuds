// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.diag

import io.github.librebuds.protocol.util.hexToBytes
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameLogTest {
    @Test
    fun writesOneJsonLinePerFrame() {
        var now = 1000L
        val log = FrameLog(capacity = 10, clock = { now++ })
        log.record(FrameDirection.TX, "5A 00 05 00 2B 2A 01 00 42 7E".hexToBytes())
        log.record(FrameDirection.RX, "5A 00 07 00 2B 2A 01 02 00 00 15 31".hexToBytes())
        assertEquals(
            "{\"ts\":1000,\"dir\":\"tx\",\"hex\":\"5A 00 05 00 2B 2A 01 00 42 7E\"}\n" +
                "{\"ts\":1001,\"dir\":\"rx\",\"hex\":\"5A 00 07 00 2B 2A 01 02 00 00 15 31\"}",
            log.toJsonl(),
        )
    }

    @Test
    fun dropsOldestBeyondCapacity() {
        val log = FrameLog(capacity = 2, clock = { 0L })
        log.record(FrameDirection.TX, byteArrayOf(1))
        log.record(FrameDirection.TX, byteArrayOf(2))
        log.record(FrameDirection.TX, byteArrayOf(3))
        assertEquals(2, log.size())
        assertEquals(false, log.toJsonl().contains("\"hex\":\"01\""))
    }
}
