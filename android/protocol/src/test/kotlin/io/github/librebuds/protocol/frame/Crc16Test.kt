package io.github.librebuds.protocol.frame

import io.github.librebuds.protocol.util.hexToBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Crc16Test {
    @Test
    fun matchesStandardCheckValue() {
        assertEquals(0x31C3, Crc16.xmodem("123456789".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun tableStartsWithDocumentedValues() {
        // Standard CRC-16/XMODEM table: the table must begin 0, 4129, 8258, 12387.
        assertEquals(listOf(0, 4129, 8258, 12387), Crc16.tableHead(4))
    }

    @Test
    fun matchesBatteryRequestFrame() {
        val frame = "5A 00 09 00 01 08 01 00 02 00 03 00 FB B9".hexToBytes()
        assertEquals(0xFBB9, Crc16.xmodem(frame, 0, frame.size - 2))
    }

    @Test
    fun crcOverWholeValidFrameIsZero() {
        val frame = "5A 00 09 00 01 08 01 00 02 00 03 00 FB B9".hexToBytes()
        assertEquals(0, Crc16.xmodem(frame))
    }
}
