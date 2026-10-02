// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.util.hexToBytes
import io.github.librebuds.protocol.util.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Request bytes and reply parsing for the feature commands, against synthetic frames shaped like the vendor app's. */
class FeatureCodecsTest {
    private fun hex(packet: Packet): String = packet.toPayload().toHex()

    private fun payload(hex: String): Packet = Packet.fromPayload(hex.hexToBytes())!!

    @Test
    fun abilityQueryIsTheVendorFrame() {
        assertEquals(
            "2B B3 01 01 01 0A 01 00 02 01 00 03 01 00 04 01 00 09 01 00 0B 01 00 0E 01 00 12 01 00 21 00 11 00 07 01 00 " +
                "14 01 01 16 01 00 15 01 00 19 01 00 25 01 00 29 01 00 2A 00 2B 00 36 01 00",
            hex(FeatureSwitch.abilityQuery()),
        )
        assertTrue(hex(FeatureSwitch.abilityQuery(first = false)).startsWith("2B B3 01 01 00 0A 01 00"))
    }

    @Test
    fun abilityAnswerListsOfferedFeatures() {
        val answer = FeatureSwitch.parseAbilities(payload("2B B3 01 01 01 07 01 01 0B 01 00 03 01 01 21 00"))!!
        assertTrue(answer.needsReply)
        assertEquals(mapOf(7 to 1, 0x0B to 0, 3 to 1), answer.capabilities)
        assertTrue(answer.offers(Feature.SINGLE_BUD_ANC))
        assertTrue(answer.offers(Feature.HEAD_CONTROL))
        assertTrue(answer.offers(Feature.ADAPTIVE_VOLUME))
        assertFalse(answer.offers(Feature.DROP_DETECTION))
        assertFalse(answer.offers(Feature.EAR_TIP), "the ear tip type has no ability entry")
        assertFalse(FeatureSwitch.parseAbilities(payload("2B B3 01 01 00 09 01 00"))!!.needsReply)
        assertNull(FeatureSwitch.parseAbilities(payload("2B B4 01 01 05 02 01 01")))
    }

    @Test
    fun featureReadsAndWrites() {
        assertEquals("2B B4 01 01 05 02 00", hex(FeatureSwitch.read(Feature.SINGLE_BUD_ANC)))
        assertEquals("2B B4 01 01 02 02 00", hex(FeatureSwitch.read(Feature.ADAPTIVE_VOLUME)))
        assertEquals("2B B4 01 01 1B 02 00", hex(FeatureSwitch.read(Feature.AI_CONVERSATION)))
        assertEquals("2B B4 01 01 07 02 00", hex(FeatureSwitch.read(Feature.DROP_DETECTION)))
        assertEquals("2B B4 01 01 08 02 00", hex(FeatureSwitch.read(Feature.EAR_TIP)))
        assertEquals("2B B4 01 01 0B", hex(FeatureSwitch.read(Feature.HEAD_CONTROL)))
        assertEquals("2B B4 01 01 05 02 01 01", hex(FeatureSwitch.write(Feature.SINGLE_BUD_ANC, 1)))
        assertEquals("2B B4 01 01 08 02 01 02", hex(FeatureSwitch.write(Feature.EAR_TIP, 2)))
        assertEquals("2B B4 01 01 0B 02 01 00", hex(FeatureSwitch.write(Feature.HEAD_CONTROL, 0)))
        assertEquals("2B B4 01 01 0B 03 01 01", hex(FeatureSwitch.writeField(Feature.HEAD_CONTROL, FeatureSwitch.NOD, 1)))
        assertEquals("2B B4 01 01 0B 04 01 02", hex(FeatureSwitch.writeField(Feature.HEAD_CONTROL, FeatureSwitch.SHAKE, 2)))
        assertThrows<IllegalArgumentException> { FeatureSwitch.writeField(Feature.HEAD_CONTROL, 5, 1) }
    }

    @Test
    fun featureStateAnswers() {
        val head = FeatureSwitch.parseState(payload("2B B4 01 01 0B 02 01 01 03 01 01 04 01 02"))!!
        assertEquals(FeatureState(0x0B, 1, 1, 2), head)
        assertTrue(FeatureSwitch.answers(Feature.HEAD_CONTROL, payload("2B B4 01 01 0B 02 01 01")))
        assertFalse(FeatureSwitch.answers(Feature.SINGLE_BUD_ANC, payload("2B B4 01 01 0B 02 01 01")))
        assertEquals(FeatureState(5, 0), FeatureSwitch.parseState(payload("2B B4 01 01 05 02 01 00")))
        assertNull(FeatureSwitch.parseState(payload("2B B4 02 01 00")), "no key, no state")
        assertEquals(Feature.EAR_TIP, Feature.ofKey(8))
    }

    @Test
    fun pinchFramesMatchTheVendorTables() {
        // The vendor's touch-setting tables send these bytes for the pinch slots.
        assertEquals("2B 93 01 01 00 02 01 01", hex(Pinch.read(PinchSlot(0, 1))))
        assertEquals("2B 93 01 01 03 02 01 00", hex(Pinch.read(PinchSlot(3, 0))))
        assertEquals("2B 92 01 01 01 02 01 02 03 01 04 04 01 04", hex(Pinch.write(PinchSlot(1, 2), 4, 4)))
        assertEquals("2B 92 01 01 00 02 01 01 03 01 FF 04 01 FF", hex(Pinch.write(PinchSlot(0, 1), Pinch.NONE, Pinch.NONE)))
        assertEquals("2B 92 01 01 03 02 01 00 03 01 06", hex(Pinch.write(PinchSlot(3, 0), 6, null)))
        assertEquals("2B 92 01 01 03 02 01 00 04 01 05", hex(Pinch.write(PinchSlot(3, 0), null, 5)))
        assertThrows<IllegalArgumentException> { Pinch.write(PinchSlot(0, 2), null, null) }
    }

    @Test
    fun pinchAnswerIsUnsigned() {
        val answer = Pinch.parse(payload("2B 93 01 01 02 02 01 02 03 01 FF 04 01 03"))!!
        assertEquals(PinchSetting(PinchSlot(2, 2), Pinch.NONE, 3), answer)
        assertTrue(Pinch.answers(PinchSlot(2, 2), payload("2B 93 01 01 02 02 01 02 03 01 03 04 01 03")))
        assertFalse(Pinch.answers(PinchSlot(1, 2), payload("2B 93 01 01 02 02 01 02 03 01 03 04 01 03")))
        assertNull(Pinch.parse(payload("2B 93 03 01 03")))
    }

    @Test
    fun findEarbudsFrames() {
        assertEquals("2B 5D 01 02 00 00", hex(FindEarbuds.ring(Side.LEFT)))
        assertEquals("2B 5D 01 02 01 01", hex(FindEarbuds.stop(Side.RIGHT)))
        assertEquals("2B 5E 01 01 00", hex(FindEarbuds.query(Side.LEFT)))
        assertEquals(Side.RIGHT to true, FindEarbuds.parseState(payload("2B 5E 02 02 01 00")))
        assertEquals(Side.LEFT to false, FindEarbuds.parseState(payload("2B 5E 02 02 00 01")))
        assertEquals(RingReport(Side.LEFT, 0), FindEarbuds.parseResult(payload("2B 5D 02 02 00 00")))
        assertNull(FindEarbuds.parseState(payload("2B 5D 02 02 00 00")), "a ring answer is not a state report")
        assertTrue(FindEarbuds.answers(Side.RIGHT, payload("2B 5D 02 02 01 00")))
        assertFalse(FindEarbuds.answers(Side.LEFT, payload("2B 5D 02 02 01 00")))
    }

    @Test
    fun byteSwitches() {
        assertEquals("2B 61 01 00", hex(RestReminder.read()))
        assertEquals("2B 60 01 01 01", hex(RestReminder.write(1)))
        assertEquals("2B 46 01 01", hex(HdCall.read()), "TLV 1 declares one byte and carries none, as the vendor app sends it")
        assertEquals("2B 45 01 01 00", hex(HdCall.write(0)))
        assertEquals("2B 42 01 01", hex(PickupMode.read()))
        assertEquals("2B 41 01 01 01", hex(PickupMode.write(PickupMode.VOICES)))
        assertEquals(1, RestReminder.parse(payload("2B 61 01 01 01 02 08 00 00 00 78 00 00 00 03")))
        assertEquals(0, HdCall.parse(payload("2B 46 01 01 00")))
        assertEquals(PickupMode.SURROUNDINGS, PickupMode.parse(payload("2B 42 01 01 00")))
        assertEquals(true, RestReminder.parseAck(payload("2B 60 7F 04 00 01 86 A0")))
        assertEquals(false, HdCall.parseAck(payload("2B 45 7F 04 00 01 86 A3")), "only 100000 counts as success")
        assertNull(PickupMode.parseAck(payload("2B 42 01 01 00")))
    }

    @Test
    fun ancAwarenessFrames() {
        assertEquals("2B 04 01 02 02 01", hex(Anc.writeRequest(AncMode.AWARENESS, Anc.AWARENESS_VOICE)))
        assertEquals("2B 04 01 02 02 04", hex(Anc.writeRequest(AncMode.AWARENESS, Anc.AWARENESS_ADAPTIVE)))
        assertEquals("2B 04 01 02 02 04 03 01 01 04 01 07", hex(Anc.awarenessLevelRequest(7)))
        assertThrows<IllegalArgumentException> { Anc.awarenessLevelRequest(11) }
        val state = Anc.parseState(payload("2B 2A 01 02 04 02 02 01 06"))!!
        assertEquals(AncState(modeCode = 2, level = 4, awarenessLevel = 6), state)
        assertTrue(Anc.confirmsAwareness(state, Anc.AWARENESS_ADAPTIVE))
        assertFalse(Anc.confirmsAwareness(state, Anc.AWARENESS_VOICE))
        assertNull(Anc.parseState(payload("2B 2A 01 02 03 01"))!!.awarenessLevel)
    }

    @Test
    fun equalizerIdsAreUnsigned() {
        val state = Equalizer.parse(payload("2B 4A 02 01 C9 03 05 05 02 09 C9 FF"))!!
        assertEquals(201, state.active)
        assertEquals(listOf(5, 2, 9, 201), state.available)
    }

    @Test
    fun customPresetRecords() {
        val gains = listOf(-60, -10, 0, 5, 10, 20, 30, 40, 50, 60)
        val record = byteArrayOf(100, 10) + ByteArray(10) { gains[it].toByte() } + "Mine".toByteArray().copyOf(24)
        val tlv8 = "08 24 " + record.toHex()
        val state = Equalizer.parse(payload("2B 4A 02 01 64 03 02 01 02 $tlv8"))!!
        assertEquals(listOf(CustomPreset(100, gains, "Mine")), state.custom)
        assertEquals(101, Equalizer.freeSlot(state.custom))
        assertNull(Equalizer.freeSlot((100..102).map { CustomPreset(it, gains, "x") }))
    }

    @Test
    fun customAndExtendedWritesUseTheFullForm() {
        val hifi = listOf(-5, 20, 30, 10, 0, 0, -25, -10, 10, 0)
        assertEquals(
            "2B 49 01 01 C9 02 01 0A 05 01 01 03 0A FB 14 1E 0A 00 00 E7 F6 0A 00 04 03 32 30 31",
            hex(Equalizer.selectExtended(201, hifi)),
        )
        val gains = listOf(10, 0, 0, 0, 0, 0, 0, 0, 0, -10)
        assertEquals(
            "2B 49 01 01 65 02 01 0A 05 01 00 03 0A 0A 00 00 00 00 00 00 00 00 F6 04 04 4D 69 6E 65",
            hex(Equalizer.writeCustom(101, gains, "Mine", EqOperation.PREVIEW)),
        )
        assertTrue(hex(Equalizer.writeCustom(102, gains, "A", EqOperation.DELETE)).contains("05 01 02"))
        assertThrows<IllegalArgumentException> { Equalizer.writeCustom(100, gains.dropLast(1), "A", EqOperation.SAVE) }
        assertThrows<IllegalArgumentException> { Equalizer.writeCustom(100, gains.map { 61 }, "A", EqOperation.SAVE) }
        assertThrows<IllegalArgumentException> { Equalizer.writeCustom(100, gains, "", EqOperation.SAVE) }
    }

    @Test
    fun extendedPresetQuery() {
        assertEquals("2B A8 01 01", hex(Equalizer.extendedQuery()))
        assertEquals(true, Equalizer.parseExtended(payload("2B A8 01 01 01")))
        assertEquals(false, Equalizer.parseExtended(payload("2B A8 01 01 00")))
        assertEquals(false, Equalizer.parseExtended(payload("2B A8 7F 04 00 01 86 A3")))
        assertNull(Equalizer.parseExtended(payload("2B 4A 01 01 01")))
    }
}
