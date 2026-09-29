package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.packetOf
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BatteryTest {
    @Test
    fun buildsRequestFrame() {
        assertEquals("5A 00 09 00 01 08 01 00 02 00 03 00 FB B9", Battery.request().toFrame().toHex())
    }

    @Test
    fun parsesPro2Reply() {
        val state = Battery.parse(packetOf("5A 00 10 00 01 08 01 01 4A 02 03 64 54 4A 03 03 00 00 00 55 36"))
        assertEquals(BatteryState(74, 100, 84, 74, false, false, false), state)
    }

    @Test
    fun parsesPushWithChargingCase() {
        val state = Battery.parse(packetOf("5A 00 10 00 01 27 01 01 5A 02 03 64 5A 30 03 03 00 00 01 E7 9E"))
        assertEquals(BatteryState(90, 100, 90, 48, false, false, true), state)
    }

    @Test
    fun ignoresOtherCommands() {
        assertNull(Battery.parse(packetOf("5A 00 07 00 2B 2A 01 02 00 00 15 31")))
    }

    @Test
    fun missingFieldsBecomeNull() {
        val state = Battery.parse(Packet(CommandId(1, 8), listOf(Tlv.of(1, 50))))
        assertEquals(BatteryState(50, null, null, null, null, null, null), state)
    }

    @Test
    fun singleLevelDeviceReportsOnlyOverall() {
        val state = Battery.parse(Packet(CommandId(1, 8), listOf(Tlv.of(1, 40), Tlv.of(2, 40), Tlv.of(3, 1))))
        assertEquals(BatteryState(40, null, null, null, null, null, null), state)
    }

    @Test
    fun extraTrailingBytesInLevelsAndChargingAreIgnored() {
        val state = Battery.parse(
            Packet(CommandId(1, 8), listOf(Tlv.of(1, 74), Tlv.of(2, 100, 84, 74, 0xFF), Tlv.of(3, 0, 0, 0))),
        )
        assertEquals(BatteryState(74, 100, 84, 74, false, false, false), state)
    }
}
