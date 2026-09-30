// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.diag

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.command.DeviceInfoCommand
import io.github.librebuds.protocol.command.HostAction
import io.github.librebuds.protocol.command.Multipoint
import io.github.librebuds.protocol.frame.Crc16
import io.github.librebuds.protocol.frame.FrameReassembler
import io.github.librebuds.protocol.frame.RxEvent
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.hexToBytes
import io.github.librebuds.protocol.util.toHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRedactorTest {
    // 11:22:33:44:55:66 on the wire, least-significant byte first.
    private val macWire = "66 55 44 33 22 11".hexToBytes()
    private val serial = "TESTSERIAL000001"
    private val budSerials = "BUDSERIALLEFT01,BUDSERIALRIGHT1"

    private val deviceInfoReply = Packet(
        DeviceInfoCommand.GET,
        listOf(
            Tlv(3, "HW-PLATFORM".toByteArray()),
            Tlv(7, "FW 1.0.0.100".toByteArray()),
            Tlv(9, serial.toByteArray()),
            Tlv(10, "SKU-A".toByteArray()),
            Tlv(15, "SKU-B".toByteArray()),
            Tlv(24, budSerials.toByteArray()),
            Tlv(2, "UNKNOWN-FIELD".toByteArray()),
            Tlv(27, macWire),
        ),
    ).toFrame()

    private fun hostRow(name: String) = Packet(
        Multipoint.ENUMERATE,
        listOf(Tlv.of(2, 1), Tlv.of(3, 0), Tlv(4, macWire), Tlv(9, name.toByteArray()), Tlv.of(5, 1)),
    ).toFrame()

    /** The packet the app would decode from [frame], or fails when the CRC or framing is broken. */
    private fun decode(frame: ByteArray): Packet {
        val payload = FrameReassembler().feed(frame).filterIsInstance<RxEvent.Payload>().single()
        return Packet.fromPayload(payload.bytes)!!
    }

    private fun single(frame: ByteArray): FrameRedactor.Result = FrameRedactor.redact(listOf(frame)).single()

    @Test
    fun deviceInfoReplyKeepsOnlyAllowlistedFields() {
        val result = single(deviceInfoReply)
        assertTrue(result.redacted)
        val bytes = requireNotNull(result.bytes)
        assertEquals(deviceInfoReply.size, bytes.size)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertFalse(text.contains("TESTSERIAL"))
        assertFalse(text.contains("BUDSERIAL"))
        assertFalse(text.contains("UNKNOWN"))
        val info = DeviceInfoCommand.parse(decode(bytes))!!
        assertEquals("X".repeat(serial.length), info.serial)
        assertEquals("X".repeat(budSerials.length), info.text(24))
        assertEquals("X".repeat("UNKNOWN-FIELD".length), info.text(2))
        assertEquals("00:00:00:00:55:66", info.macAddress)
        assertEquals("HW-PLATFORM", info.platform)
        assertEquals("FW 1.0.0.100", info.firmware)
        assertEquals("SKU-A", info.text(10))
        assertEquals("SKU-B", info.text(15))
    }

    @Test
    fun hostRowAddressAndNameAreMasked() {
        val result = single(hostRow("Jan's Pixel"))
        assertTrue(result.redacted)
        val row = Multipoint.parseRow(decode(result.bytes!!))!!
        assertEquals("00:00:00:00:55:66", row.mac)
        assertEquals("X".repeat("Jan's Pixel".length), row.name)
        assertEquals(0, row.index)
        assertTrue(row.connected)
    }

    @Test
    fun hostRequestsAreMasked() {
        for (packet in listOf(Multipoint.setPreferred("11:22:33:44:55:66"), Multipoint.execute(HostAction.CONNECT, "11:22:33:44:55:66"))) {
            val result = single(packet.toFrame())
            assertTrue(result.redacted)
            val value = decode(result.bytes!!).tlvs.single().value
            assertArrayEquals("66 55 00 00 00 00".hexToBytes(), value)
        }
    }

    @Test
    fun changePushRecordsWithAnAddressOrNameAreMasked() {
        val push = Packet(Multipoint.CHANGED, listOf(Tlv.of(1, 1), Tlv(2, macWire), Tlv(3, "Ann".toByteArray()), Tlv.of(4, 0, 9))).toFrame()
        val tlvs = decode(single(push).bytes!!).tlvs
        assertArrayEquals(byteArrayOf(1), tlvs[0].value)
        assertArrayEquals("66 55 00 00 00 00".hexToBytes(), tlvs[1].value)
        assertEquals("XXX", String(tlvs[2].value))
        assertArrayEquals(byteArrayOf(0, 9), tlvs[3].value)
    }

    @Test
    fun framesOverTheReassemblerLimitAreNotParsed() {
        // A CRC-valid frame the app itself would drop as too long is not exported either.
        val big = Packet(CommandId(0x2B, 0x2A), List(17) { Tlv(1, ByteArray(250)) }).toFrame()
        assertTrue(big.size > FrameReassembler.DEFAULT_MAX_FRAME)
        assertNull(single(big).bytes)
    }

    @Test
    fun unrelatedFramesAndRequestsAreUnchanged() {
        val frames = listOf(
            "5A 00 05 00 2B 2A 01 00 42 7E".hexToBytes(),
            "5A 00 07 00 2B 2A 01 02 00 00 15 31".hexToBytes(),
            DeviceInfoCommand.request().toFrame(),
            Multipoint.enumerate().toFrame(),
            Packet(CommandId(0x2B, 0x36), listOf(Tlv.of(1, 1))).toFrame(),
        )
        for (frame in frames) {
            val result = single(frame)
            assertFalse(frame.toHex(), result.redacted)
            assertArrayEquals(frame, result.bytes)
        }
    }

    @Test
    fun severalFramesInOneReadAndOneFrameAcrossTwoReads() {
        val other = "5A 00 05 00 2B 2A 01 00 42 7E".hexToBytes()
        val split = deviceInfoReply.size / 2
        val results = FrameRedactor.redact(
            listOf(other + hostRow("Laptop"), deviceInfoReply.copyOfRange(0, split), deviceInfoReply.copyOfRange(split, deviceInfoReply.size)),
        )
        assertTrue(results.all { it.bytes != null && it.redacted })
        val rejoined = results[1].bytes!! + results[2].bytes!!
        assertEquals("00:00:00:00:55:66", DeviceInfoCommand.parse(decode(rejoined))!!.macAddress)
        assertArrayEquals(other, results[0].bytes!!.copyOfRange(0, other.size))
    }

    @Test
    fun fragmentedReplyIsRedactedAcrossFragments() {
        val payload = Packet(DeviceInfoCommand.GET, listOf(Tlv(9, serial.toByteArray()), Tlv(27, macWire))).toPayload()
        val cut = 10
        val parts = listOf(payload.copyOfRange(0, cut), payload.copyOfRange(cut, payload.size))
        val fragments = parts.mapIndexed { index, part -> fragment(if (index == 0) 1 else 3, index, part) }
        val results = FrameRedactor.redact(fragments)
        assertTrue(results.all { it.redacted })
        val events = FrameReassembler().feed(results[0].bytes!! + results[1].bytes!!)
        val info = DeviceInfoCommand.parse(Packet.fromPayload(events.filterIsInstance<RxEvent.Payload>().single().bytes)!!)!!
        assertEquals("X".repeat(serial.length), info.serial)
        assertEquals("00:00:00:00:55:66", info.macAddress)
    }

    @Test
    fun unparsableEntriesAreNotExported() {
        val results = FrameRedactor.redact(
            listOf(
                "01 02 03".hexToBytes(),
                // A frame whose CRC does not match.
                deviceInfoReply.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() },
                // The start of a frame the log never saw the rest of.
                deviceInfoReply.copyOfRange(0, 8),
            ),
        )
        results.forEach { assertNull(it.bytes) }
    }

    @Test
    fun exportMarksRedactedLinesAndHidesUnparsedBytes() {
        val log = FrameLog(clock = { 5L })
        log.record(FrameDirection.TX, DeviceInfoCommand.request().toFrame())
        log.record(FrameDirection.RX, deviceInfoReply)
        log.record(FrameDirection.RX, "01 02 03".hexToBytes())
        val lines = log.toExportJsonl().split("\n")
        assertFalse(lines[0].contains("redacted"))
        assertTrue(lines[1].endsWith(",\"redacted\":true}"))
        assertFalse(lines[1].contains(Tlv(9, serial.toByteArray()).value.toHex()))
        assertEquals("{\"type\":\"frame\",\"ts\":5,\"dir\":\"rx\",\"hex\":\"unparsed\",\"length\":3}", lines[2])
        // The buffer itself stays raw.
        assertTrue(log.toJsonl().contains(serial.toByteArray().toHex()))
    }

    private fun fragment(flag: Int, index: Int, data: ByteArray): ByteArray {
        val body = byteArrayOf(0x5A, 0, 0, flag.toByte(), index.toByte()) + data
        val length = body.size - 3
        body[1] = (length ushr 8).toByte()
        body[2] = length.toByte()
        val crc = Crc16.xmodem(body)
        return body + byteArrayOf((crc ushr 8).toByte(), crc.toByte())
    }
}
