// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import io.github.librebuds.protocol.beacon.FdeeBeacon
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.protocol.util.hexToBytes
import org.junit.Assert.assertEquals
import org.junit.Test

class BeaconBatchTest {
    private val rules = PopupRules()
    private val registry = ProfileRegistry.fromJson(listOf("""{"id":"freebuds-6","name":"FreeBuds 6","match":{"modelId":["000155"]}}"""))

    // Close range, reference RSSI -70 (threshold -80), model 000155.
    private val known = "01 01 01 02 BA 03 00 01 55 0C E4".hexToBytes()
    private val unknownModel = "01 01 01 02 BA 03 00 09 99 0C E4".hexToBytes()

    private fun judge(results: List<RawSighting>, lastShownAt: (String) -> Long? = { null }, associated: Profile? = null) =
        judgeBatch(results, now = 500_000L, rules = rules, registry = registry, associated = associated, lastShownAt = lastShownAt)

    @Test
    fun showsOncePerBatch() {
        // The same case reported three times, twice under a new random address.
        val verdicts = judge(
            listOf(
                RawSighting("11:11:11:11:11:11", -50, known),
                RawSighting("11:11:11:11:11:11", -50, known),
                RawSighting("22:22:22:22:22:22", -50, known),
            )
        )
        assertEquals(listOf(PopupDecision.SHOW, PopupDecision.IGNORE_COOLDOWN, PopupDecision.IGNORE_COOLDOWN), verdicts.map { it.decision })
    }

    @Test
    fun unknownIgnoredUnlessAssociated() {
        val batch = listOf(RawSighting("11:11:11:11:11:11", -50, unknownModel))
        assertEquals(PopupDecision.IGNORE_UNKNOWN, judge(batch).single().decision)
        assertEquals(PopupDecision.SHOW, judge(batch, associated = ProfileRegistry.fromJson(listOf("""{"id":"assoc","name":"Assoc","match":{"modelId":["000999"]}}""")).profiles.single()).single().decision)
    }

    @Test
    fun farIgnored() {
        assertEquals(PopupDecision.IGNORE_FAR, judge(listOf(RawSighting("11:11:11:11:11:11", -90, known))).single().decision)
    }

    @Test
    fun storedCooldownAppliesAcrossAddresses() {
        val shownAt = mapOf(cooldownKey(FdeeBeacon.parse(known)!!) to 490_000L)
        val verdicts = judge(listOf(RawSighting("33:33:33:33:33:33", -50, known)), lastShownAt = { shownAt[it] })
        assertEquals(PopupDecision.IGNORE_COOLDOWN, verdicts.single().decision)
    }

    @Test
    fun unparsableAndMissingDataSkipped() {
        val verdicts = judge(listOf(RawSighting("11:11:11:11:11:11", -50, null), RawSighting("11:11:11:11:11:11", -50, "03 00".hexToBytes())))
        assertEquals(emptyList<BeaconVerdict>(), verdicts)
    }
}
