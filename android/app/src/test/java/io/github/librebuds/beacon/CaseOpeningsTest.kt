// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import io.github.librebuds.protocol.beacon.BeaconBattery
import io.github.librebuds.protocol.beacon.FdeeBeacon
import io.github.librebuds.protocol.util.hexToBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaseOpeningsTest {
    // FreeBuds Pro 3 frames from hardware round 2.
    private val closed = FdeeBeacon.parse("01 02 00 3F 00 00 00 01 49 00 03 44 AB 00".hexToBytes())!!
    private val open = FdeeBeacon.parse("01 02 03 FF 0C 00 00 01 49 00 03 42 AB 30 64 64 F0 11".hexToBytes())!!
    private val openCharging = FdeeBeacon.parse("01 02 03 FF 0C 00 00 01 49 00 03 4A AB 30 E2 E4 F0 10".hexToBytes())!!
    private val budsOut = FdeeBeacon.parse("01 02 03 DF 0C 00 00 01 49 00 03 8B AB 64 64 F0 10".hexToBytes())!!
    private val justClosed = FdeeBeacon.parse("01 02 00 BF 00 00 00 01 49 00 03 84 AB 2F E4".hexToBytes())!!

    @Test
    fun openFrameStartsAnOpeningWithItsBatteries() {
        val opening = CaseOpenings.step(null, open, 1_000)!!
        assertTrue(opening.open)
        assertFalse(opening.shown)
        assertEquals(BudBatteries(BeaconBattery(100, false), BeaconBattery(100, false), BeaconBattery(48, false)), opening.batteries)
    }

    @Test
    fun furtherOpenFramesContinueTheOpening() {
        val shown = CaseOpenings.step(null, open, 1_000)!!.copy(shown = true)
        val next = CaseOpenings.step(shown, openCharging, 6_000)!!
        assertTrue(next.shown)
        assertEquals(6_000, next.lastOpenAt)
        assertEquals(BeaconBattery(98, true), next.batteries.left)
    }

    @Test
    fun closedFrameEndsTheOpeningAndTheNextOpenStartsANewOne() {
        val shown = CaseOpenings.step(null, open, 1_000)!!.copy(shown = true)
        val closedState = CaseOpenings.step(shown, closed, 2_000)!!
        assertFalse(closedState.open)
        assertEquals(BudBatteries(), closedState.batteries)
        val reopened = CaseOpenings.step(closedState, open, 3_000)!!
        assertTrue(reopened.open)
        assertFalse(reopened.shown)
        assertFalse(CaseOpenings.step(shown, justClosed, 2_000)!!.open)
    }

    @Test
    fun openFrameAfterALongSilenceIsANewOpening() {
        val shown = CaseOpenings.step(null, open, 1_000)!!.copy(shown = true)
        assertTrue(CaseOpenings.step(shown, open, 1_000 + CaseOpenings.GAP_MILLIS)!!.shown)
        assertFalse(CaseOpenings.step(shown, open, 1_001 + CaseOpenings.GAP_MILLIS)!!.shown)
        // A clock set back also starts over.
        assertFalse(CaseOpenings.step(shown, open, 500)!!.shown)
    }

    @Test
    fun budsOutNeitherStartsNorEndsAnOpening() {
        assertNull(CaseOpenings.step(null, budsOut, 1_000))
        val opening = CaseOpenings.step(null, open, 1_000)!!.copy(shown = true)
        val out = CaseOpenings.step(opening, budsOut, 2_000)!!
        assertTrue(out.open && out.shown)
        assertEquals(1_000, out.lastOpenAt)
        // Bud levels refresh, the case level stays the last one seen.
        assertEquals(BeaconBattery(48, false), out.batteries.case)
        val closedState = CaseOpenings.step(null, closed, 1_000)
        assertNull(closedState)
    }

    @Test
    fun encodeDecodeRoundTripAndPrune() {
        val openings = mapOf(
            "000149/0" to CaseOpenings.step(null, openCharging, 4_000_000)!!.copy(shown = true),
            "000141/0" to Opening(open = false, lastOpenAt = 100, shown = false, batteries = BudBatteries()),
        )
        assertEquals(openings, CaseOpenings.decode(CaseOpenings.encode(openings)))
        assertEquals(setOf("000149/0"), CaseOpenings.prune(openings, 4_000_100).keys)
        assertEquals(emptyMap<String, Opening>(), CaseOpenings.decode("not json"))
        assertEquals(emptyMap<String, Opening>(), CaseOpenings.decode("""{"k":{"open":true}}"""))
    }
}
