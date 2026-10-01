// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.components

import io.github.librebuds.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductArtTest {
    @Test
    fun stemShapeGetsStemRenders() {
        assertEquals(R.drawable.battery_buds_stem, ProductArt.battery("stem"))
        assertEquals(R.drawable.thumb_buds_stem, ProductArt.thumbnail("stem"))
    }

    @Test
    fun roundGenericAndUnknownShapesFallBackToRoundRenders() {
        for (shape in listOf("round", "generic", "eyewear", "")) {
            assertEquals(R.drawable.battery_buds_round, ProductArt.battery(shape))
            assertEquals(R.drawable.thumb_buds_round, ProductArt.thumbnail(shape))
        }
    }
}
