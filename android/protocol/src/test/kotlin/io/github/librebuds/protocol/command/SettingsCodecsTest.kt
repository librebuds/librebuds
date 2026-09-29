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
    fun swipeWritesBothSides() {
        assertEquals("2B 1E 01 01 00 02 01 00", Gestures.write(Gesture.SWIPE, left = 0).toPayload().toHex())
    }

    @Test
    fun equalizerParseAndSelect() {
        assertEquals("2B 4A 01 00 02 00 03 00 04 00 05 00 06 00 07 00 08 00", Equalizer.read().toPayload().toHex())
        val reply = Packet(CommandId(0x2B, 0x4A), listOf(Tlv.of(2, 5), Tlv.of(3, 5, 1, 2, 9)))
        assertEquals(EqualizerState(active = 5, available = listOf(5, 1, 2, 9)), Equalizer.parse(reply))
        assertEquals("2B 49 01 01 09", Equalizer.select(9).toPayload().toHex())
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
