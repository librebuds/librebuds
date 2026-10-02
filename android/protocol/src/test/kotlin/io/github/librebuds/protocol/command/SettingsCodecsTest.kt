// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SettingsCodecsTest {
    @Test
    fun wearDetectionRoundTrip() {
        assertEquals("2B 11 01 00", WearDetection.read().toPayload().toHex())
        assertEquals("2B 10 01 01 01", WearDetection.write(true).toPayload().toHex())
        assertEquals(true, WearDetection.parse(Packet(CommandId(0x2B, 0x11), listOf(Tlv.of(1, 1)))))
        assertNull(WearDetection.parse(Packet(CommandId(0x2B, 0x10), listOf(Tlv.of(1, 1)))))
    }

    @Test
    fun inEarFromTlv8Or9() {
        assertEquals(true, WearDetection.parseInEar(Packet(CommandId(0x2B, 0x03), listOf(Tlv.of(8, 1)))))
        assertEquals(false, WearDetection.parseInEar(Packet(CommandId(0x2B, 0x03), listOf(Tlv.of(9, 0)))))
        assertNull(WearDetection.parseInEar(Packet(CommandId(0x2B, 0x03), listOf(Tlv.of(1, 1)))))
    }

    @Test
    fun gestureReadWriteAndSignedValues() {
        assertEquals("01 20 01 00 02 00", Gestures.read(Gesture.DOUBLE_TAP).toPayload().toHex())
        assertEquals("01 20 01 00 02 00 04 00", Gestures.read(Gesture.DOUBLE_TAP, withInCall = true).toPayload().toHex())
        assertEquals("01 1F 01 01 FF", Gestures.write(Gesture.DOUBLE_TAP, left = -1).toPayload().toHex())
        val reply = Packet(CommandId(0x01, 0x20), listOf(Tlv.of(1, 1), Tlv.of(2, 0xFF), Tlv.of(3, 0xFF, 0, 1, 2, 7)))
        assertEquals(GestureSetting(left = 1, right = -1, inCall = null, supported = listOf(-1, 0, 1, 2, 7)), Gestures.parse(Gesture.DOUBLE_TAP, reply))
    }

    @Test
    fun gestureWithMissingRightIsPartial() {
        val reply = Packet(CommandId(0x2B, 0x17), listOf(Tlv.of(1, 10)))
        assertEquals(GestureSetting(left = 10, right = null, inCall = null, supported = emptyList()), Gestures.parse(Gesture.LONG_PRESS, reply))
    }

    @Test
    fun swipeWritesBothSidesExactlyAsTheVendorApp() {
        // TLV 2 declares two bytes for its one-byte value: the literal frame of the earbuds' own app.
        assertEquals("2B 1E 01 01 00 02 02 00", Gestures.write(Gesture.SWIPE, left = 0).toPayload().toHex())
        assertEquals("2B 1E 01 01 FF 02 02 FF", Gestures.write(Gesture.SWIPE, left = -1).toPayload().toHex())
        assertEquals("2B 1E 01 01 FF 02 02 FF", Gestures.write(Gesture.SWIPE, left = -1, bothSides = true).toPayload().toHex())
    }

    @Test
    fun gestureWritesOneSidePerFrame() {
        assertEquals("01 25 02 01 07", Gestures.write(Gesture.TRIPLE_TAP, right = 7).toPayload().toHex())
        assertEquals("2B 16 04 01 00", Gestures.write(Gesture.LONG_PRESS, inCall = 0).toPayload().toHex())
        assertEquals("2B 16 01 01 03", Gestures.write(Gesture.LONG_PRESS, left = 3).toPayload().toHex())
    }

    @Test
    fun bothSidesGestureCarriesOneValueInTlv1AndTlv2() {
        assertEquals("01 1F 01 01 01 02 01 01", Gestures.write(Gesture.DOUBLE_TAP, left = 1, right = 7, bothSides = true).toPayload().toHex())
    }

    @Test
    fun gestureParseReadsInCallList() {
        // FreeBuds 5 double-tap reply: TLV 3 accepted codes, TLV 4/5 in-call, TLV 6 accepted in-call codes.
        val reply = Packet.fromPayload(byteArrayOf(0x01, 0x20, 1, 1, 1, 2, 1, 1, 3, 5, 1, 7, 2, 0, -1, 4, 1, 0, 5, 1, 0, 6, 2, 0, -1))!!
        assertEquals(
            GestureSetting(left = 1, right = 1, inCall = 0, supported = listOf(1, 7, 2, 0, -1), inCallSupported = listOf(0, -1)),
            Gestures.parse(Gesture.DOUBLE_TAP, reply),
        )
    }

    @Test
    fun gestureAckStatusBytes() {
        fun ack(g: Gesture, type: Int, value: Int) = Gestures.parseAck(g, Packet(g.set, listOf(Tlv.of(type, value))))
        assertEquals(GestureAck.ACCEPTED, ack(Gesture.DOUBLE_TAP, 3, 0))
        assertEquals(GestureAck.REJECTED, ack(Gesture.DOUBLE_TAP, 3, 1))
        assertEquals(GestureAck.ACCEPTED, ack(Gesture.TRIPLE_TAP, 3, 2))
        assertEquals(GestureAck.REJECTED, ack(Gesture.TRIPLE_TAP, 3, 3))
        assertEquals(GestureAck.ACCEPTED, ack(Gesture.LONG_PRESS, 6, 0))
        assertEquals(GestureAck.REJECTED, ack(Gesture.LONG_PRESS, 6, 1))
        // Swipe writes both sides in one frame: one refused side refuses the change.
        val both = Packet(Gesture.SWIPE.set, listOf(Tlv.of(3, 0), Tlv.of(3, 3)))
        assertEquals(GestureAck.REJECTED, Gestures.parseAck(Gesture.SWIPE, both))
        assertEquals(GestureAck.REJECTED, Gestures.parseAck(Gesture.SWIPE, Packet(Gesture.SWIPE.set, listOf(Tlv.of(0x7F, 0, 1, 0x86, 0xA3)))))
        assertNull(Gestures.parseAck(Gesture.SWIPE, Packet(Gesture.SWIPE.set, listOf(Tlv.of(2, 0)))))
        // A read reply is not an ack.
        assertNull(Gestures.parseAck(Gesture.DOUBLE_TAP, Packet(Gesture.DOUBLE_TAP.get, listOf(Tlv.of(3, 1)))))
    }

    @Test
    fun equalizerParseAndSelect() {
        assertEquals("2B 4A 01 00 02 00 03 00 04 00 05 00 06 00 07 00 08 00", Equalizer.read().toPayload().toHex())
        val reply = Packet(CommandId(0x2B, 0x4A), listOf(Tlv.of(2, 5), Tlv.of(3, 5, 1, 2, 9)))
        assertEquals(EqualizerState(active = 5, available = listOf(5, 1, 2, 9)), Equalizer.parse(reply))
        assertEquals("2B 49 01 01 09", Equalizer.select(9).toPayload().toHex())
    }

    @Test
    fun equalizerSelectAckIsTheStatusRecord() {
        // The FreeBuds 5 answer to 2B 49 01 01 <id>: 2B 49 7F 04 00 01 86 A0 (status 100000).
        assertEquals(true, Equalizer.parseAck(Packet.fromPayload(byteArrayOf(0x2B, 0x49, 0x7F, 4, 0, 1, 0x86.toByte(), 0xA0.toByte()))!!))
        assertEquals(false, Equalizer.parseAck(Packet.fromPayload(byteArrayOf(0x2B, 0x49, 0x7F, 4, 0, 1, 0x86.toByte(), 0xA3.toByte()))!!))
        assertNull(Equalizer.parseAck(Packet(CommandId(0x2B, 0x49), listOf(Tlv.of(2, 0)))))
        assertNull(Equalizer.parseAck(Packet(CommandId(0x2B, 0x4A), listOf(Tlv.of(0x7F, 0, 1, 0x86, 0xA0)))))
    }

    @Test
    fun equalizerWithoutAvailableList() {
        assertEquals(EqualizerState(active = 1, available = emptyList()), Equalizer.parse(Packet(CommandId(0x2B, 0x4A), listOf(Tlv.of(2, 1)))))
    }

    @Test
    fun lowLatencyUsesTlv2ForReadAndTlv1ForWrite() {
        assertEquals("2B 6C 02 00", LowLatency.read().toPayload().toHex())
        assertEquals("2B 6C 01 01 01", LowLatency.write(true).toPayload().toHex())
        assertEquals(false, LowLatency.parse(Packet(CommandId(0x2B, 0x6C), listOf(Tlv.of(2, 0)))))
        assertNull(LowLatency.parse(Packet(CommandId(0x2B, 0x6C), listOf(Tlv.of(1, 0)))))
    }

    @Test
    fun lowLatencyProbeIsItsOwnRequest() {
        assertEquals("2B 6C 03 00", LowLatency.probe().toPayload().toHex())
        assertEquals(0, LowLatency.parseSupport(Packet(CommandId(0x2B, 0x6C), listOf(Tlv.of(3, 0)))))
        assertEquals(1, LowLatency.parseSupport(Packet(CommandId(0x2B, 0x6C), listOf(Tlv.of(3, 1)))))
        assertNull(LowLatency.parseSupport(Packet(CommandId(0x2B, 0x6C), listOf(Tlv.of(0x7F, 0, 1, 0x86, 0xA3)))))
    }

    @Test
    fun soundQualityCapabilityOnTlv1() {
        val reply = Packet.fromPayload(byteArrayOf(0x2B, 0xA3.toByte(), 1, 1, 1, 2, 1, 0))!!
        assertEquals(1, SoundQuality.parseCapability(reply))
        assertEquals(0, SoundQuality.parse(reply))
        assertNull(SoundQuality.parseCapability(Packet(CommandId(0x2B, 0xA3), listOf(Tlv.of(2, 1)))))
    }

    @Test
    fun soundQualityReadsTlv2() {
        assertEquals("2B A3 01 00", SoundQuality.read().toPayload().toHex())
        assertEquals("2B A2 01 01 01", SoundQuality.write(1).toPayload().toHex())
        assertEquals(1, SoundQuality.parse(Packet(CommandId(0x2B, 0xA3), listOf(Tlv.of(2, 1)))))
    }

    @Test
    fun voiceLanguageIsReadOnly() {
        assertEquals("0C 02 01 00 02 00 03 00", VoiceLanguage.read().toPayload().toHex())
        val reply = Packet(CommandId(0x0C, 0x02), listOf(Tlv(1, "en-GB".toByteArray()), Tlv(3, "en-GB,pl-PL,de-DE".toByteArray())))
        assertEquals(LanguageInfo("en-GB", listOf("en-GB", "pl-PL", "de-DE")), VoiceLanguage.parse(reply))
    }
}
