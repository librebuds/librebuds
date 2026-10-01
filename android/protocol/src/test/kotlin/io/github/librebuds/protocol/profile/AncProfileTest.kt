// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.profile

import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.repoRoot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

class AncProfileTest {
    private val registry: ProfileRegistry by lazy {
        val files = File(repoRoot(), "profiles").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        ProfileRegistry.fromJson(files.map { it.readText() })
    }

    private fun profile(anc: String?): Profile {
        val capabilities = if (anc == null) """{"battery":{}}""" else """{"anc":$anc}"""
        return ProfileRegistry.fromJson(listOf("""{"id":"x","name":"X","capabilities":$capabilities}""")).profiles.single()
    }

    @Test
    fun freeBuds5OffersOffAndCancellationWithThreeLevels() {
        val fb5 = registry.profiles.first { it.id == "freebuds-5" }
        assertEquals(listOf(AncMode.OFF, AncMode.CANCELLATION), fb5.ancModes())
        assertEquals(listOf(3, 1, 0), fb5.cancellationLevels())
        assertEquals(listOf("dynamic", "cozy", "general"), fb5.cancellationLevels().map { fb5.cancellationLevelKey(it) })
    }

    @Test
    fun otherSweptModelsKeepAllModesAndDynamicOnly() {
        for (id in listOf("freebuds-6", "freebuds-pro-2", "freebuds-pro-3")) {
            val p = registry.profiles.first { it.id == id }
            assertEquals(AncMode.entries.toList(), p.ancModes(), id)
            assertEquals(listOf(3), p.cancellationLevels(), id)
        }
    }

    @Test
    fun profilesWithoutModesOfferAllThree() {
        assertEquals(AncMode.entries.toList(), profile("""{"verified":null}""").ancModes())
        assertEquals(AncMode.entries.toList(), ProfileRegistry.GENERIC.ancModes())
        assertEquals(AncMode.entries.toList(), registry.profiles.first { it.id == "freebuds-pro-4" }.ancModes())
        assertEquals(emptyList<Int>(), ProfileRegistry.GENERIC.cancellationLevels())
    }

    @Test
    fun noAncCapabilityMeansNoModes() {
        assertEquals(emptyList<AncMode>(), profile(null).ancModes())
        assertEquals(emptyList<Int>(), profile(null).cancellationLevels())
    }

    @Test
    fun unknownModeNamesAreIgnoredAndOrderFollowsTheProtocol() {
        assertEquals(listOf(AncMode.OFF, AncMode.AWARENESS), profile("""{"modes":["awareness","adaptive","off"]}""").ancModes())
    }

    @Test
    fun levelsKeepProfileOrderAndDropDuplicatesAndJunk() {
        assertEquals(listOf(2, 0, 3), profile("""{"cancellationLevels":[2,0,"x",2,3,255,-1]}""").cancellationLevels())
    }

    @Test
    fun levelsNeedCancellationMode() {
        assertEquals(emptyList<Int>(), profile("""{"modes":["off","awareness"],"cancellationLevels":[3,1]}""").cancellationLevels())
    }

    @Test
    fun levelNamesDefaultToTheSharedMeaningAndCanBeOverridden() {
        val plain = profile("""{"cancellationLevels":[0,1,2,3,5]}""")
        assertEquals(listOf("general", "cozy", "ultra", "dynamic", null), plain.cancellationLevels().map { plain.cancellationLevelKey(it) })
        val named = profile("""{"cancellationLevels":[2],"cancellationLevelNames":{"2":"dynamic"}}""")
        assertEquals("dynamic", named.cancellationLevelKey(2))
        assertNull(named.cancellationLevelKey(7))
    }
}
