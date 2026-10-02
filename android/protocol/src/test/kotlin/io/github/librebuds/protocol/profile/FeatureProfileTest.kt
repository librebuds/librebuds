// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.profile

import io.github.librebuds.protocol.command.Feature
import io.github.librebuds.protocol.command.PinchSlot
import io.github.librebuds.protocol.repoRoot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The per-model feature lists the shipped profiles carry, as the vendor app's model configuration allows them. */
class FeatureProfileTest {
    private val registry: ProfileRegistry by lazy {
        val files = File(repoRoot(), "profiles").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        ProfileRegistry.fromJson(files.map { it.readText() })
    }

    private fun profile(id: String) = registry.profiles.first { it.id == id }

    private val tapSlots = listOf(PinchSlot(0, 1), PinchSlot(1, 1), PinchSlot(0, 2), PinchSlot(1, 2), PinchSlot(2, 2))
    private val symphony = listOf(15, 15, 10, -5, 15, 25, 15, -5, 50, 45)
    private val hifiLive = listOf(-5, 20, 30, 10, 0, 0, -25, -10, 10, 0)

    @Test
    fun pro5MatchesBothProductIds() {
        assertEquals("freebuds-pro-5", registry.match(modelId = "00016B").id)
        assertEquals("freebuds-pro-5", registry.match(modelId = "00016D").id)
    }

    @Test
    fun pro5() {
        val p = profile("freebuds-pro-5")
        assertEquals(listOf(2, 1, 4), p.awarenessModes())
        assertTrue(p.hasAwarenessLevel())
        assertEquals(tapSlots + PinchSlot(3, 0), p.pinchSlots().map { it.slot })
        assertEquals(listOf(6, 5, 0xFF), p.pinchSlots().last().options.map { it.code })
        assertEquals(
            setOf(Feature.SINGLE_BUD_ANC, Feature.ADAPTIVE_VOLUME, Feature.AI_CONVERSATION, Feature.HEAD_CONTROL, Feature.EAR_TIP),
            p.features().toSet(),
        )
        assertEquals(mapOf(201 to hifiLive), p.extendedPresets())
        assertTrue(p.customEqualizer())
        assertEquals(listOf("1" to "answer", "2" to "reject", "0" to "off"), p.headActions().map { it.code.toString() to it.key })
        for (key in listOf("findEarbuds", "restReminder", "pickupMode")) assertTrue(p.supports(key), key)
        assertFalse(p.supports("hdCall"))
    }

    @Test
    fun pro4() {
        val p = profile("freebuds-pro-4")
        assertEquals(listOf(2, 1), p.awarenessModes())
        assertFalse(p.hasAwarenessLevel())
        assertEquals(tapSlots, p.pinchSlots().map { it.slot })
        assertEquals(setOf(Feature.HEAD_CONTROL, Feature.EAR_TIP), p.features().toSet())
        assertEquals(mapOf(200 to symphony, 201 to hifiLive), p.extendedPresets())
        assertTrue(p.supports("pickupMode"))
        assertFalse(p.supports("hdCall"))
    }

    @Test
    fun pro2AndPro3() {
        for (id in listOf("freebuds-pro-2", "freebuds-pro-3")) {
            val p = profile(id)
            assertEquals(listOf(2, 1), p.awarenessModes(), id)
            assertEquals(tapSlots, p.pinchSlots().map { it.slot }, id)
            assertEquals(emptyList<Feature>(), p.features(), id)
            assertEquals(setOf(200, 201), p.extendedPresets().keys, id)
        }
        assertTrue(profile("freebuds-pro-2").supports("hdCall"))
        assertFalse(profile("freebuds-pro-2").supports("pickupMode"))
        assertFalse(profile("freebuds-pro-3").supports("hdCall"), "the vendor app hides HD calls on Pro 3")
        assertTrue(profile("freebuds-pro-3").supports("pickupMode"))
    }

    @Test
    fun freebuds6() {
        val p = profile("freebuds-6")
        assertEquals(emptyList<Int>(), p.awarenessModes(), "no awareness on FreeBuds 6")
        assertEquals(setOf(Feature.SINGLE_BUD_ANC, Feature.DROP_DETECTION, Feature.ADAPTIVE_VOLUME, Feature.HEAD_CONTROL), p.features().toSet())
        assertEquals(emptyList<PinchSlotSpec>(), p.pinchSlots())
        assertEquals(setOf(200, 201), p.extendedPresets().keys)
        assertTrue(p.supports("pickupMode"))
    }

    @Test
    fun freebuds5And4() {
        val fb5 = profile("freebuds-5")
        assertTrue(fb5.customEqualizer())
        assertTrue(fb5.supports("hdCall"))
        assertEquals(emptyMap<Int, List<Int>>(), fb5.extendedPresets())
        val fb4 = profile("freebuds-4")
        assertFalse(fb4.customEqualizer(), "the vendor app offers no custom presets on FreeBuds 4")
        assertTrue(fb4.supports("hdCall"))
        assertTrue(fb4.supports("pickupMode"))
        for (p in listOf(fb5, fb4)) {
            assertTrue(p.supports("findEarbuds"))
            assertTrue(p.supports("restReminder"))
            assertEquals(emptyList<Feature>(), p.features())
        }
    }
}
