package io.github.librebuds.protocol.beacon

import io.github.librebuds.protocol.util.hexToBytes
import io.github.librebuds.protocol.util.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FdeeBeaconTest {
    @Test
    fun parsesPopupBeacon() {
        val beacon = FdeeBeacon.parse("01 01 01 02 BA 03 00 01 55 04 01 0C E4 0D 50 0E 30 11 C4".hexToBytes())!!
        assertEquals(1, beacon.businessType)
        assertEquals(1, beacon.businessFlags)
        assertTrue(beacon.closeRangeEnabled)
        assertFalse(beacon.reconnectEnabled)
        assertEquals(-70, beacon.referenceRssi)
        assertEquals("000155", beacon.modelId)
        assertEquals(1, beacon.subModelId)
        assertEquals(BeaconBattery(100, charging = true), beacon.left)
        assertEquals(BeaconBattery(80, charging = false), beacon.right)
        assertEquals(BeaconBattery(48, charging = false), beacon.case)
        assertNull(beacon.total)
        assertEquals(-60, beacon.advPower)
    }

    @Test
    fun readsReconnectFlagAndOtherBusinessTypes() {
        val both = FdeeBeacon.parse("01 01 03".hexToBytes())!!
        assertTrue(both.closeRangeEnabled)
        assertTrue(both.reconnectEnabled)
        val other = FdeeBeacon.parse("01 03 01".hexToBytes())!!
        assertEquals(3, other.businessType)
        assertFalse(other.closeRangeEnabled)
    }

    @Test
    fun requiresBusinessRecord() {
        assertNull(FdeeBeacon.parse("03 00 01 55".hexToBytes()))
    }

    @Test
    fun truncatedValueInvalidatesBeacon() {
        assertNull(FdeeBeacon.parse("01 01 01 03 00 01".hexToBytes()))
    }

    @Test
    fun duplicateRecordInvalidatesBeacon() {
        assertNull(FdeeBeacon.parse("01 01 01 03 00 01 55 03 00 01 55".hexToBytes()))
    }

    @Test
    fun deviceIdRecordsConcatenate() {
        val beacon = FdeeBeacon.parse("01 01 01 05 AA BB 05 CC DD".hexToBytes())!!
        assertEquals(listOf("AABB", "CCDD"), beacon.deviceIds)
    }

    @Test
    fun unknownTypeStopsWalkButKeepsEarlierFields() {
        val beacon = FdeeBeacon.parse("01 01 01 03 00 01 55 13 99 0C 64".hexToBytes())!!
        assertEquals("000155", beacon.modelId)
        assertNull(beacon.left)
    }

    @Test
    fun capturesTrailingCustomField() {
        val beacon = FdeeBeacon.parse("01 01 01 FF 01 02 03".hexToBytes())!!
        assertEquals("01 02 03", beacon.custom!!.toHex())
    }

    @Test
    fun emptyAndTinyInputsReturnNull() {
        assertNull(FdeeBeacon.parse(ByteArray(0)))
        assertNull(FdeeBeacon.parse("01".hexToBytes()))
        assertNull(FdeeBeacon.parse("FF".hexToBytes()))
    }

    @Test
    fun readsNewModelIdAsAscii() {
        val beacon = FdeeBeacon.parse("01 01 01 12 41 42 43 44".hexToBytes())
        assertNotNull(beacon)
        assertEquals("00ABCD", beacon!!.newModelId)
    }

    @Test
    fun compactFrameReadsHeadBatteriesAndLid() {
        // FreeBuds 5, lid open: case 89%, left 100%, right 100% charging.
        val beacon = FdeeBeacon.parse("01 02 03 FF 0C 00 00 01 41 00 03 4A AB 59 64 E4 F0 50".hexToBytes())!!
        assertEquals(BeaconFormat.COMPACT, beacon.format)
        assertEquals("000141", beacon.modelId)
        assertEquals(0, beacon.subModelId)
        assertEquals(0x03FF, beacon.flags)
        assertEquals(LidState.OPEN, beacon.lid)
        assertTrue(beacon.caseOpen)
        assertFalse(beacon.closeRangeEnabled)
        assertEquals(0x4A, beacon.state)
        assertEquals(BeaconBattery(89, charging = false), beacon.case)
        assertEquals(BeaconBattery(100, charging = false), beacon.left)
        assertEquals(BeaconBattery(100, charging = true), beacon.right)
        assertEquals(listOf(0xF0, 0x50), beacon.tail)
    }

    @Test
    fun compactFlagsSayWhichBatteriesArePresent() {
        // Pro 4 after closing: 007F carries case + left, 00BF case + right.
        val left = FdeeBeacon.parse("01 02 00 7F 00 00 00 01 57 16 03 44 AB 00 64".hexToBytes())!!
        assertNull(left.case) // 0x00 = not reported
        assertEquals(100, left.left?.percent)
        assertNull(left.right)
        assertEquals(LidState.TRANSITIONAL, left.lid)
        val right = FdeeBeacon.parse("01 02 00 BF 00 00 00 01 57 16 03 84 AB 64 64".hexToBytes())!!
        assertEquals(100, right.case?.percent)
        assertNull(right.left)
        assertEquals(100, right.right?.percent)
        // Both buds out: no case byte, so the two batteries are left and right.
        val out = FdeeBeacon.parse("01 02 03 DF 0C 00 00 01 31 01 03 8B AB 64 63 EF 10".hexToBytes())!!
        assertEquals(LidState.BUDS_OUT, out.lid)
        assertFalse(out.caseOpen)
        assertNull(out.case)
        assertEquals(99, out.right?.percent)
        val idle = FdeeBeacon.parse("01 02 00 3F 00 00 00 01 49 00 03 44 AB 2D".hexToBytes())!!
        assertEquals(LidState.CLOSED, idle.lid)
        assertEquals(45, idle.case?.percent)
    }

    @Test
    fun compactFrameShorterThanItsFlagsIsRejected() {
        for (hex in listOf("01 02", "01 02 03 FF", "01 02 03 FF 0C 00 00 01 41 00 03 42 AB 59 64 64 F0")) {
            assertNull(FdeeBeacon.parse(hex.hexToBytes()), hex)
        }
        // Extra trailing bytes past the flagged fields are ignored.
        assertEquals("000141", FdeeBeacon.parse("01 02 00 3F 00 00 00 01 41 00 03 44 AB 00 99".hexToBytes())?.modelId)
    }

    @Test
    fun extendedFramesAreNotDecoded() {
        assertNull(FdeeBeacon.parse("05 03 00 05 0C 12 5A 41 41 4C 04 30 32 11 00 0B 64".hexToBytes()))
        assertNull(FdeeBeacon.parse("00 10 00 01 00 00 00 00 00 00 02 5A 41 41 4C 03 30 32".hexToBytes()))
    }
}
