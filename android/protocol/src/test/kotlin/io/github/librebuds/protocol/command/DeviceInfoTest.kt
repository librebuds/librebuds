package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.hexToBytes
import io.github.librebuds.protocol.util.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DeviceInfoTest {
    private fun text(type: Int, value: String) = Tlv(type, value.toByteArray(Charsets.UTF_8))

    @Test
    fun buildsRequestFrame() {
        assertEquals(
            "5A 00 1D 00 01 07 01 00 02 00 03 00 04 00 05 00 06 00 07 00 08 00 09 00 0A 00 0F 00 18 00 1B 00 3C 5D",
            DeviceInfoCommand.request().toFrame().toHex(),
        )
    }

    @Test
    fun parsesFreeBuds6Fields() {
        val reply = Packet(
            CommandId(1, 7),
            listOf(
                text(3, "PLATFORM_A"),
                text(7, "FW 1.0.0.100"),
                text(9, "TESTSERIAL000001"),
                text(15, "BTFT0020"),
                Tlv(27, "66 55 44 33 22 11".hexToBytes()),
            ),
        )
        val info = DeviceInfoCommand.parse(Packet.fromPayload(reply.toPayload())!!)!!
        assertEquals("PLATFORM_A", info.platform)
        assertEquals("FW 1.0.0.100", info.firmware)
        assertEquals("TESTSERIAL000001", info.serial)
        assertEquals("BTFT0020", info.sku)
        assertEquals("11:22:33:44:55:66", info.macAddress)
    }

    @Test
    fun prefersTlv10ForSku() {
        val info = DeviceInfoCommand.parse(Packet(CommandId(1, 7), listOf(text(10, "BTFT0013"), text(15, "OTHER"))))!!
        assertEquals("BTFT0013", info.sku)
    }

    @Test
    fun splitsSkuAndModelIdFromTlv10() {
        // FreeBuds 5 (HCI capture): TLV 10 "<SKU>-<model id>", TLV 15 the bare SKU.
        val info = DeviceInfoCommand.parse(Packet(CommandId(1, 7), listOf(text(10, "BTFT0013-000141"), text(15, "BTFT0013"))))!!
        assertEquals("BTFT0013", info.sku)
        assertEquals("000141", info.modelId)
        assertNull(DeviceInfoCommand.parse(Packet(CommandId(1, 7), listOf(text(15, "BTFT0013"))))!!.modelId)
    }

    @Test
    fun trimsNulPaddingAndTreatsEmptyAsNull() {
        val info = DeviceInfoCommand.parse(
            Packet(CommandId(1, 7), listOf(Tlv(3, "PLATFORM_B\u0000\u0000".toByteArray()), Tlv.empty(7))),
        )!!
        assertEquals("PLATFORM_B", info.platform)
        assertNull(info.firmware)
        assertNull(info.macAddress)
    }

    @Test
    fun ignoresOtherCommands() {
        assertNull(DeviceInfoCommand.parse(Packet(CommandId(1, 8), emptyList())))
    }
}
