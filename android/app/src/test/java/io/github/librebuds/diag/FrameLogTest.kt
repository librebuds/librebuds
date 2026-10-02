// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.diag

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.hexToBytes
import io.github.librebuds.protocol.util.toHex
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
            "{\"type\":\"frame\",\"ts\":1000,\"dir\":\"tx\",\"hex\":\"5A 00 05 00 2B 2A 01 00 42 7E\"}\n" +
                "{\"type\":\"frame\",\"ts\":1001,\"dir\":\"rx\",\"hex\":\"5A 00 07 00 2B 2A 01 02 00 00 15 31\"}",
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

    @Test
    fun logsOneLinePerReassembledFrameAndSkipsPrivateOnes() {
        val lines = mutableListOf<String>()
        val log = FrameLog(capacity = 10, clock = { 0L }, logcat = { lines += it })
        val battery = "5A 00 07 00 2B 2A 01 02 00 00 15 31".hexToBytes()
        // One frame split over two reads, then a read holding a whole frame plus the start of the next.
        log.record(FrameDirection.RX, battery.copyOfRange(0, 5))
        assertEquals(emptyList<String>(), lines)
        log.record(FrameDirection.RX, battery.copyOfRange(5, battery.size) + battery + battery.copyOfRange(0, 3))
        log.record(FrameDirection.RX, battery.copyOfRange(3, battery.size))
        assertEquals(List(3) { "RX 5A 00 07 00 2B 2A 01 02 00 00 15 31" }, lines)
        lines.clear()
        // Device info replies and multipoint host rows/pushes stay out; the device info request is logged.
        val info = Packet(CommandId(0x01, 0x07), listOf(Tlv(9, "TESTSERIAL000001".toByteArray()))).toFrame()
        log.record(FrameDirection.RX, info)
        log.record(FrameDirection.RX, Packet(CommandId(0x2B, 0x31), listOf(Tlv.of(1, 0))).toFrame())
        log.record(FrameDirection.TX, Packet(CommandId(0x2B, 0x36), listOf(Tlv.of(1, 1))).toFrame())
        log.record(FrameDirection.TX, Packet.read(CommandId(0x01, 0x07), 1).toFrame())
        assertEquals(listOf("TX 5A 00 05 00 01 07 01 00 ${Packet.read(CommandId(0x01, 0x07), 1).toFrame().toHex().takeLast(5)}"), lines)
        // The buffer keeps every raw chunk.
        assertEquals(7, log.size())
    }
}
