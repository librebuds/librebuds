package io.github.librebuds.protocol.tlv

import io.github.librebuds.protocol.util.hexToBytes
import io.github.librebuds.protocol.util.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TlvTest {
    @Test
    fun parsesSequentialRecords() {
        val tlvs = Tlv.parse("01 01 64 02 03 64 54 4A".hexToBytes())
        assertEquals(listOf(Tlv.of(1, 0x64), Tlv.of(2, 0x64, 0x54, 0x4A)), tlvs)
    }

    @Test
    fun parsesZeroLengthRecords() {
        assertEquals(listOf(Tlv.empty(1), Tlv.empty(2)), Tlv.parse("01 00 02 00".hexToBytes()))
    }

    @Test
    fun clampsOverLongValueToEndOfData() {
        val tlvs = Tlv.parse("02 05 AA BB".hexToBytes())
        assertEquals(1, tlvs.size)
        assertEquals("AA BB", tlvs[0].value.toHex())
    }

    @Test
    fun ignoresLoneTrailingTypeByte() {
        assertEquals(listOf(Tlv.of(1, 0x00)), Tlv.parse("01 01 00 07".hexToBytes()))
    }

    @Test
    fun startsAtGivenOffset() {
        assertEquals(listOf(Tlv.empty(1)), Tlv.parse("01 08 01 00".hexToBytes(), start = 2))
    }

    @Test
    fun encodesRecords() {
        assertEquals("01 00 02 02 01 03", Tlv.encode(listOf(Tlv.empty(1), Tlv.of(2, 1, 3))).toHex())
    }
}
