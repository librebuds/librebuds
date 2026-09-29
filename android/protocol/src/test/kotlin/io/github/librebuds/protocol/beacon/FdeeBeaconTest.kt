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
        val other = FdeeBeacon.parse("01 02 01".hexToBytes())!!
        assertEquals(2, other.businessType)
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
}
