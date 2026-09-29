package io.github.librebuds.protocol.frame

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.hexToBytes
import io.github.librebuds.protocol.util.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LinkFrameTest {
    @Test
    fun encodesBatteryRequest() {
        val frame = LinkFrame.encode("01 08 01 00 02 00 03 00".hexToBytes())
        assertEquals("5A 00 09 00 01 08 01 00 02 00 03 00 FB B9", frame.toHex())
    }

    @Test
    fun encodesAncReadRequest() {
        assertEquals("5A 00 05 00 2B 2A 01 00 42 7E", LinkFrame.encode("2B 2A 01 00".hexToBytes()).toHex())
    }

    @Test
    fun packetBuildsFrameFromTlvs() {
        val packet = Packet(CommandId(0x2B, 0x04), listOf(Tlv.of(1, 0x01, 0x03)))
        assertEquals("5A 00 07 00 2B 04 01 02 01 03 D1 7F", packet.toFrame().toHex())
    }

    @Test
    fun readRequestHasEmptyTlvs() {
        assertEquals("01 08 01 00 02 00 03 00", Packet.read(CommandId(1, 8), 1, 2, 3).toPayload().toHex())
    }

    @Test
    fun packetRoundTripsThroughPayload() {
        val packet = Packet.fromPayload("01 08 01 01 4A".hexToBytes())!!
        assertEquals("01/08", packet.id.toString())
        assertEquals("4A", packet.find(1)!!.toHex())
        assertNull(packet.find(2))
    }

    @Test
    fun payloadShorterThanTwoBytesIsNotAPacket() {
        assertNull(Packet.fromPayload("01".hexToBytes()))
    }
}
