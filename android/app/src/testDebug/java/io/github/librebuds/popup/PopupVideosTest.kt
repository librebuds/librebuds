// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import io.github.librebuds.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** Debug builds bundle the generated clips for the round shape only. */
class PopupVideosTest {
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
