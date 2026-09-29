// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import io.github.librebuds.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PopupArtTest {
    private val videos = mapOf("round" to (11 to 12))

    @Test
    fun videoVariantUsesClipsWhenAvailable() {
        val art = artFor("round", ArtVariant.VIDEO, videos)
        assertEquals(ArtVariant.VIDEO, art.variant)
        assertEquals(11, art.videoLight)
    }

    @Test
    fun fallsBackToVectorWithoutClip() {
        assertEquals(ArtVariant.VECTOR, artFor("stem", ArtVariant.VIDEO, videos).variant)
    }

    @Test
    fun unknownShapeUsesGeneric() {
        assertEquals(artFor("generic", ArtVariant.VECTOR, videos).avdRes, artFor("banana", ArtVariant.VECTOR, videos).avdRes)
    }

    @Test
    fun roundShapeHasBundledClips() {
        assertEquals(R.raw.popup_round_light to R.raw.popup_round_dark, PopupVideos.map()["round"])
        val art = artFor("round", ArtVariant.VIDEO, PopupVideos.map())
        assertEquals(R.raw.popup_round_dark, art.videoDark)
    }

    @Test
    fun onlyRoundShapeHasBundledClips() {
        assertEquals(setOf("round"), PopupVideos.map().keys)
        assertEquals(ArtVariant.VECTOR, artFor("stem", ArtVariant.VIDEO, PopupVideos.map()).variant)
    }
}
