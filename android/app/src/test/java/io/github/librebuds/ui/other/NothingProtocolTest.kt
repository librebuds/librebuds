package io.github.librebuds.ui.other

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NothingProtocolTest {
    @Test fun crcMatchesModbusReference() {
        assertEquals(0x4B37, NothingProtocol.crc16("123456789".toByteArray()))
    }

    @Test fun parsesFragmentedFrameAndRejectsInvalidCrc() {
        val encoded = NothingProtocol.frame(NothingProtocol.GET_BATTERY, 2, byteArrayOf(1, 2, 88))
        val parser = NothingProtocol.Parser()
        assertTrue(parser.feed(encoded.copyOfRange(0, 4)).isEmpty())
        assertTrue(parser.feed(encoded.copyOfRange(4, 7)).isEmpty())
        val frames = parser.feed(encoded.copyOfRange(7, encoded.size))
        assertEquals(1, frames.size)
        assertEquals(NothingProtocol.GET_BATTERY, frames.first().command)
        assertArrayEquals(byteArrayOf(1, 2, 88), frames.first().payload)
        val broken = encoded.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 0x40).toByte() }
        assertTrue(NothingProtocol.Parser().feed(broken).isEmpty())
    }

    @Test fun readsBatteryPartsAndChargingFlag() {
        val payload = byteArrayOf(3, 2, (0x80 or 63).toByte(), 3, 92, 4, 45)
        assertEquals(mapOf(2 to 63, 3 to 92, 4 to 45), NothingProtocol.battery(payload))
        assertTrue(NothingProtocol.battery(byteArrayOf(4, 2, 50)).isEmpty())
    }

    @Test fun readsNoiseReportModes() {
        assertEquals(7, NothingProtocol.noiseMode(byteArrayOf(1, 7, 0, 2, 4, 0)))
        assertEquals(5, NothingProtocol.noiseMode(byteArrayOf(1, 5, 0)))
        assertEquals(null, NothingProtocol.noiseMode(byteArrayOf(2, 4, 0)))
    }

    @Test fun supportsEarOneWithoutCrc() {
        val frame = NothingProtocol.frame(NothingProtocol.GET_NOISE, 0, byteArrayOf(3), crc = false)
        assertEquals(9, frame.size)
        assertEquals(1, NothingProtocol.Parser().feed(frame).size)
    }
}
