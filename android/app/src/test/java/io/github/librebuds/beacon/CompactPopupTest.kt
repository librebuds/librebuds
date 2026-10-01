// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import io.github.librebuds.protocol.beacon.BeaconBattery
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.protocol.util.hexToBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The case-open popup from the everyday compact beacons, replayed across scan batches. */
class CompactPopupTest {
    private val rules = PopupRules()
    private val registry = ProfileRegistry.fromJson(
        listOf(
            """{"id":"freebuds-5","name":"FreeBuds 5","match":{"modelId":["000141"],"btName":["HUAWEI FreeBuds 5"]}}""",
            """{"id":"freebuds-pro-2","name":"FreeBuds Pro 2","match":{"modelId":["000131"],"btName":["HUAWEI FreeBuds Pro 2"]}}""",
        )
    )

    // FreeBuds 5 frames from hardware round 2.
    private val closed = "01 02 00 3F 00 00 00 01 41 00 03 44 AB 00".hexToBytes()
    private val open = "01 02 03 FF 0C 00 00 01 41 00 03 42 AB 59 64 64 F0 51".hexToBytes()
    private val openCharging = "01 02 03 FF 0C 00 00 01 41 00 03 4A AB 59 64 E4 F0 50".hexToBytes()
    private val worn = "01 02 03 DF 0C 00 00 01 41 00 03 8B AB 64 64 F0 10".hexToBytes()
    // A stranger's FreeBuds Pro 2, lid open.
    private val neighbourOpen = "01 02 03 FF 0C 00 00 01 31 01 03 8A AB 45 64 64 EF 11".hexToBytes()

    private val openings = mutableMapOf<String, Opening>()
    private val shownAt = mutableMapOf<String, Long>()

    private fun batch(now: Long, vararg results: Pair<ByteArray, Int>, bonded: Set<String>? = setOf("freebuds-5"), associated: Profile? = null): List<BeaconVerdict> {
        val verdicts = judgeBatch(
            results = results.map { (data, rssi) -> RawSighting("11:11:11:11:11:11", rssi, data) },
            now = now, rules = rules, registry = registry, associated = associated,
            lastShownAt = { shownAt[it] }, openings = openings, bonded = bonded,
        )
        verdicts.filter { it.decision == PopupDecision.SHOW }.forEach { shownAt[cooldownKey(it.sighting.beacon)] = now }
        return verdicts
    }

    private fun decisions(verdicts: List<BeaconVerdict>) = verdicts.map { it.decision }

    @Test
    fun showsOnceWhenTheLidOpens() {
        assertEquals(listOf(PopupDecision.IGNORE_NOT_OPEN), decisions(batch(0, closed to -60)))
        val first = batch(5_000, open to -60, open to -58, openCharging to -55)
        assertEquals(listOf(PopupDecision.SHOW, PopupDecision.IGNORE_SHOWN, PopupDecision.IGNORE_SHOWN), decisions(first))
        assertEquals("freebuds-5", first[0].profile?.id)
        assertEquals(BudBatteries(BeaconBattery(100, false), BeaconBattery(100, false), BeaconBattery(89, false)), first[0].batteries)
        // The case stays open: no more popups, even after the cooldown.
        assertEquals(listOf(PopupDecision.IGNORE_SHOWN), decisions(batch(30_000, open to -60)))
        assertEquals(listOf(PopupDecision.IGNORE_SHOWN), decisions(batch(55_000, open to -60)))
    }

    @Test
    fun closingAndOpeningAgainShowsAgain() {
        batch(0, open to -60)
        assertEquals(listOf(PopupDecision.IGNORE_NOT_OPEN), decisions(batch(20_000, closed to -60)))
        assertEquals(listOf(PopupDecision.SHOW), decisions(batch(30_000, open to -60)))
    }

    @Test
    fun openFrameAfterBeingAwayIsANewOpening() {
        batch(0, open to -60)
        // The closed frame was missed; the case is seen open again a minute later.
        assertEquals(listOf(PopupDecision.SHOW), decisions(batch(60_000, open to -60)))
    }

    @Test
    fun farFirstFrameDoesNotUseUpTheOpening() {
        assertEquals(listOf(PopupDecision.IGNORE_FAR), decisions(batch(0, open to -85)))
        assertEquals(listOf(PopupDecision.SHOW), decisions(batch(5_000, open to -70)))
    }

    @Test
    fun wornBudsNeverShow() {
        assertEquals(listOf(PopupDecision.IGNORE_NOT_OPEN, PopupDecision.IGNORE_NOT_OPEN), decisions(batch(0, worn to -50, worn to -50)))
        // Taken out of an open case: the opening was already shown.
        batch(10_000, open to -60)
        assertEquals(listOf(PopupDecision.IGNORE_NOT_OPEN), decisions(batch(15_000, worn to -50)))
    }

    @Test
    fun onlyBondedOrAddedEarbudsShow() {
        // A neighbour's Pro 2 is a known model but not bonded to this phone.
        assertEquals(listOf(PopupDecision.IGNORE_UNKNOWN), decisions(batch(0, neighbourOpen to -55)))
        // Bonded devices unreadable: every known model counts.
        assertEquals(listOf(PopupDecision.SHOW), decisions(batch(40_000, neighbourOpen to -55, bonded = null)))
        // Added in the app, not bonded.
        openings.clear(); shownAt.clear()
        val added = registry.match(modelId = "000131")
        assertEquals(listOf(PopupDecision.SHOW), decisions(batch(0, neighbourOpen to -55, bonded = emptySet(), associated = added)))
    }

    @Test
    fun addedEarbudsWithoutModelIdDoNotClaimCompactBeacons() {
        val unknownOpen = "01 02 03 FF 0C 00 00 09 99 00 03 42 AB 59 64 64 F0 51".hexToBytes()
        val added = ProfileRegistry.fromJson(listOf("""{"id":"x","name":"X","match":{"btName":["X"]}}""")).profiles.single()
        val verdict = batch(0, unknownOpen to -50, bonded = setOf("x"), associated = added).single()
        assertEquals(PopupDecision.IGNORE_UNKNOWN, verdict.decision)
        assertNull(verdict.profile)
    }

    @Test
    fun twoModelsAreTrackedSeparately() {
        val verdicts = batch(0, open to -60, neighbourOpen to -60, bonded = setOf("freebuds-5", "freebuds-pro-2"))
        assertEquals(listOf(PopupDecision.SHOW, PopupDecision.SHOW), decisions(verdicts))
        assertTrue(openings.keys.containsAll(listOf("000141/0@11:11:11:11:11:11", "000131/1@11:11:11:11:11:11")))
    }

    @Test
    fun logsOneLinePerModelAndOnlyChanges() {
        val logged = mutableMapOf<String, PopupDecision>()
        val first = decisionLogLines(batch(0, closed to -60, closed to -61), logged)
        assertEquals(listOf("ignored: case not open: FreeBuds 5 [000141/0 compact closed, -61 dBm]"), first)
        assertEquals(emptyList<String>(), decisionLogLines(batch(1_000, closed to -60), logged))
        val shown = decisionLogLines(batch(5_000, open to -60, open to -60, neighbourOpen to -70), logged)
        assertEquals(
            listOf(
                "popup shown for FreeBuds 5 [000141/0 compact open, -60 dBm]",
                "ignored: no matching bonded or added earbuds: FreeBuds Pro 2 [000131/1 compact open, -70 dBm]",
            ),
            shown,
        )
        assertEquals(listOf("ignored: already shown for this opening: FreeBuds 5 [000141/0 compact open, -60 dBm]"), decisionLogLines(batch(6_000, open to -60), logged))
        shown.forEach { assertTrue(!it.contains("11:11")) }
    }

    @Test
    fun aClosedCaseOfTheSameModelNearbyDoesNotEndTheOpening() {
        // Another FreeBuds 5, lid closed, shares the model key with the user's open case.
        assertEquals(listOf(PopupDecision.SHOW, PopupDecision.IGNORE_NOT_OPEN), decisions(batch(0, open to -60, closed to -75)))
        assertEquals(listOf(PopupDecision.IGNORE_NOT_OPEN, PopupDecision.IGNORE_SHOWN), decisions(batch(6_000, closed to -75, open to -60)))
        assertEquals(listOf(PopupDecision.IGNORE_SHOWN), decisions(batch(12_000, open to -60)))
    }

    private fun batchFrom(now: Long, vararg results: Triple<String, ByteArray, Int>): List<BeaconVerdict> {
        val verdicts = judgeBatch(
            results = results.map { (address, data, rssi) -> RawSighting(address, rssi, data) },
            now = now, rules = rules, registry = registry, associated = null,
            lastShownAt = { shownAt[it] }, openings = openings, bonded = setOf("freebuds-5"),
        )
        verdicts.filter { it.decision == PopupDecision.SHOW }.forEach { shownAt[cooldownKey(it.sighting.beacon)] = now }
        return verdicts
    }

    @Test
    fun anotherPairsClosedCaseInItsOwnBatchDoesNotRestartTheOpening() {
        // Seen on a phone with two FreeBuds 5: the closed pair on the desk sent its frames in batches
        // of their own, and every one of them ended the opening of the pair in the user's hand.
        val mine = "AA:AA:AA:AA:AA:01"
        val desk = "BB:BB:BB:BB:BB:02"
        assertEquals(listOf(PopupDecision.SHOW), decisions(batchFrom(0, Triple(mine, open, -60))))
        assertEquals(listOf(PopupDecision.IGNORE_NOT_OPEN), decisions(batchFrom(5_000, Triple(desk, closed, -72))))
        assertEquals(listOf(PopupDecision.IGNORE_SHOWN), decisions(batchFrom(5_100, Triple(mine, open, -58))))
        assertEquals(listOf(PopupDecision.IGNORE_NOT_OPEN), decisions(batchFrom(10_000, Triple(desk, closed, -70))))
        assertEquals(listOf(PopupDecision.IGNORE_SHOWN), decisions(batchFrom(10_100, Triple(mine, open, -57))))
    }
}
