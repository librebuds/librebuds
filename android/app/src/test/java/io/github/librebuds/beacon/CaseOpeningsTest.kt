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
    fun budsOutKeepsAnOpeningAlive() {
        val opening = CaseOpenings.step(null, open, 1_000)!!.copy(shown = true)
        val out = CaseOpenings.step(opening, budsOut, 2_000)!!
        assertTrue(out.open && out.shown)
        assertEquals(2_000, out.lastOpenAt)
        // Bud levels refresh, the case level stays the last one seen.
        assertEquals(BeaconBattery(48, false), out.batteries.case)
        assertNull(CaseOpenings.step(null, closed, 1_000))
    }

    @Test
    fun earbudsPutBackIntoTheOpenCaseAfterWearingThemRaiseNoPopup() {
        // Opened, popup shown, earbuds worn for minutes (buds-out frames), then put back: the open
        // frame comes long after the opening began but continues the same session.
        var state = CaseOpenings.step(null, open, 1_000)!!.copy(shown = true)
        for (t in 5_000L..300_000L step 5_000L) state = CaseOpenings.step(state, budsOut, t)!!
        val back = CaseOpenings.step(state, open, 303_000)!!
        assertTrue(back.open && back.shown)
        // Worn without a seen opening (app started while worn): still no popup when put back.
        val worn = CaseOpenings.step(null, budsOut, 1_000)!!
        assertTrue(CaseOpenings.step(worn, open, 4_000)!!.shown)
        // Closing the case and opening it again is a new opening.
        val closedAgain = CaseOpenings.step(back, closed, 305_000)!!
        assertFalse(CaseOpenings.step(closedAgain, open, 306_000)!!.shown)
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

    @Test
    fun savesOnlyMeaningfulChanges() {
        val opening = CaseOpenings.step(null, open, 10_000)!!
        val before = mapOf("k" to opening)
        assertFalse(CaseOpenings.worthSaving(before, mapOf("k" to opening.copy(lastOpenAt = 14_000))))
        assertTrue(CaseOpenings.worthSaving(before, mapOf("k" to opening.copy(lastOpenAt = 15_000))))
        assertTrue(CaseOpenings.worthSaving(before, mapOf("k" to opening.copy(shown = true))))
        assertTrue(CaseOpenings.worthSaving(before, mapOf("k" to opening.copy(open = false))))
        assertTrue(CaseOpenings.worthSaving(before, emptyMap()))
        assertTrue(CaseOpenings.worthSaving(emptyMap(), before))
    }
}
