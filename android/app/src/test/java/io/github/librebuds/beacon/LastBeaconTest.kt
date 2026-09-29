// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import io.github.librebuds.protocol.util.hexToBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastBeaconTest {
    private val sample = LastBeacon(
        serviceDataHex = "01 01 01 02 BA 03 00 01 55 0C E4",
        modelId = "000155",
        subModelId = null,
        rssi = -50,
        referenceRssi = -70,
        atMillis = 123_456L,
    )

    @Test
    fun roundTrip() {
        assertEquals(sample, LastBeacon.decode(sample.encode()))
    }

    @Test
    fun roundTripWithoutOptionalFields() {
        val minimal = LastBeacon(serviceDataHex = "01 01", modelId = null, subModelId = null, rssi = -80, referenceRssi = null, atMillis = 1L)
        assertEquals(minimal, LastBeacon.decode(minimal.encode()))
    }

    @Test
    fun missingOrCorruptTextIsNull() {
        assertNull(LastBeacon.decode(null))
        assertNull(LastBeacon.decode("not json"))
        assertNull(LastBeacon.decode("""{"modelId":"000155"}""")) // no serviceDataHex/rssi/atMillis
    }

    @Test
    fun lastBeaconOfPicksTheLastParsableResult() {
        val known = "01 01 01 02 BA 03 00 01 55 0C E4".hexToBytes()
        val results = listOf(
            RawSighting("11:11:11:11:11:11", -50, null), // no service data
            RawSighting("22:22:22:22:22:22", -60, "03 00".hexToBytes()), // truncated, unparsable
            RawSighting("33:33:33:33:33:33", -55, known),
        )
        val last = lastBeaconOf(results, now = 999L)
        assertEquals(
            LastBeacon(serviceDataHex = "01 01 01 02 BA 03 00 01 55 0C E4", modelId = "000155", subModelId = null, rssi = -55, referenceRssi = -70, atMillis = 999L),
            last,
        )
    }

    @Test
    fun lastBeaconOfNullWhenNothingParses() {
        val results = listOf(RawSighting("11:11:11:11:11:11", -50, null), RawSighting("22:22:22:22:22:22", -60, "03 00".hexToBytes()))
        assertNull(lastBeaconOf(results, now = 999L))
    }
}
