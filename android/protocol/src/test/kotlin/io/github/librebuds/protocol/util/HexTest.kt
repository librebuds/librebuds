package io.github.librebuds.protocol.util

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HexTest {
    @Test
    fun parsesSpacedAndCompactHex() {
        val expected = byteArrayOf(0x5A, 0x00, 0x09)
        assertArrayEquals(expected, "5A 00 09".hexToBytes())
        assertArrayEquals(expected, "5a0009".hexToBytes())
    }

    @Test
    fun formatsAsUpperCaseSpacedPairs() {
        assertEquals("5A 00 FF", byteArrayOf(0x5A, 0x00, -1).toHex())
        assertEquals("", ByteArray(0).toHex())
    }

    @Test
    fun rejectsOddDigitCount() {
        assertThrows(IllegalArgumentException::class.java) { "5A0".hexToBytes() }
    }

    @Test
    fun readsBytesAsUnsigned() {
        assertEquals(255, (-1).toByte().u8())
        assertEquals(90, 0x5A.toByte().u8())
    }
}
