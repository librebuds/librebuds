// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import io.github.librebuds.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PopupArtTest {
    private val videos = mapOf("freebuds-6" to (11 to 12))

    @Test
    fun profileWithClipPlaysIt() {
        val art = artFor("freebuds-6", "round", videos)
        assertEquals(ArtVariant.VIDEO, art.variant)
        assertEquals(11, art.videoLight)
        assertEquals(12, art.videoDark)
        // The drawing stays set as the fallback when the clip cannot play.
        assertEquals(R.drawable.avd_round_open, art.avdRes)
    }

    @Test
    fun profileWithoutClipFallsBackToItsShape() {
        val art = artFor("freebuds-pro-2-plus", "stem", videos)
        assertEquals(ArtVariant.VECTOR, art.variant)
        assertEquals(R.drawable.avd_stem_open, art.avdRes)
        assertNull(art.videoLight)
        assertNull(art.videoDark)
    }

    @Test
    fun unknownShapeUsesGeneric() {
        assertEquals(R.drawable.avd_generic_open, artFor("unknown", "banana", videos).avdRes)
    }

    @Test
    fun everyModelWithAClipMapsToItsResources() {
        val expected = mapOf(
            "freebuds-4" to (R.raw.popup_freebuds_4_light to R.raw.popup_freebuds_4_dark),
            "freebuds-5" to (R.raw.popup_freebuds_5_light to R.raw.popup_freebuds_5_dark),
            "freebuds-6" to (R.raw.popup_freebuds_6_light to R.raw.popup_freebuds_6_dark),
            "freebuds-pro-2" to (R.raw.popup_freebuds_pro_light to R.raw.popup_freebuds_pro_dark),
            "freebuds-pro-3" to (R.raw.popup_freebuds_pro_light to R.raw.popup_freebuds_pro_dark),
            "freebuds-pro-4" to (R.raw.popup_freebuds_pro_light to R.raw.popup_freebuds_pro_dark),
            "freebuds-pro-5" to (R.raw.popup_freebuds_pro_light to R.raw.popup_freebuds_pro_dark),
        )
        assertEquals(expected, PopupVideos.clips)
        for ((id, clip) in expected) {
            val art = artFor(id, "round")
            assertEquals(id, ArtVariant.VIDEO, art.variant)
            assertEquals(id, clip.first, art.videoLight)
            assertEquals(id, clip.second, art.videoDark)
        }
        // Eight distinct clips: FreeBuds 4, 5 and 6 have their own, the Pro models share one,
        // and light differs from dark.
        assertEquals(8, expected.values.flatMap { listOf(it.first, it.second) }.toSet().size)
    }

    @Test
    fun clipKeysAreRealProfileIds() {
        val ids = java.io.File(profilesDir(), ".").listFiles { f -> f.extension == "json" }!!.map { it.nameWithoutExtension }.toSet()
        assertEquals(emptySet<String>(), PopupVideos.clips.keys - ids)
    }

    @Test
    fun defaultMapIsTheBundledClips() {
        assertEquals(ArtVariant.VECTOR, artFor("freebuds-7", "generic").variant)
        assertEquals(ArtVariant.VIDEO, artFor("freebuds-pro-4", "stem").variant)
    }

    @Test
    fun cardTakesTheDecodedBackgroundWhenItIsClose() {
        val nominal = 0xFF1C1C1E.toInt()
        // The emulator renders the dark clip background as #2E2C30.
        assertEquals(0xFF2E2C30.toInt(), cardColorFor(nominal, 0xFF2E2C30.toInt()))
        // Alpha from the read-back is ignored: the card stays opaque.
        assertEquals(0xFF1D1C1F.toInt(), cardColorFor(nominal, 0x801D1C1F.toInt()))
    }

    @Test
    fun cardKeepsNominalColourWhenTheSampleIsNotBackground() {
        val nominal = 0xFFFFFFFF.toInt()
        assertEquals(nominal, cardColorFor(nominal, 0xFF808080.toInt()))
        assertEquals(nominal, cardColorFor(nominal, 0xFFFFFF00.toInt()))
        assertEquals(nominal, cardColorFor(nominal, null))
    }

    private fun profilesDir(): java.io.File =
        generateSequence(java.io.File("").absoluteFile) { it.parentFile }.map { java.io.File(it, "profiles") }.first { it.isDirectory }
}
