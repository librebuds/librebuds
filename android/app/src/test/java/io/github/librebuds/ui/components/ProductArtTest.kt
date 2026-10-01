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

    @Test
    fun noProfileIdFallsBackToShapeForCaseAsWell() {
        assertEquals(R.drawable.battery_case, ProductArt.case())
        assertEquals(R.drawable.battery_case, ProductArt.case(null))
        assertEquals(R.drawable.battery_case, ProductArt.case("generic"))
        assertEquals(R.drawable.battery_case, ProductArt.case("freebuds-4"))
    }

    @Test
    fun freebuds5GetsItsOwnRendersRegardlessOfShape() {
        assertEquals(R.drawable.battery_buds_freebuds_5, ProductArt.battery("round", "freebuds-5"))
        assertEquals(R.drawable.thumb_buds_freebuds_5, ProductArt.thumbnail("round", "freebuds-5"))
        assertEquals(R.drawable.battery_case_freebuds_5, ProductArt.case("freebuds-5"))
    }

    @Test
    fun freebuds6GetsItsOwnRendersRegardlessOfShape() {
        assertEquals(R.drawable.battery_buds_freebuds_6, ProductArt.battery("round", "freebuds-6"))
        assertEquals(R.drawable.thumb_buds_freebuds_6, ProductArt.thumbnail("round", "freebuds-6"))
        assertEquals(R.drawable.battery_case_freebuds_6, ProductArt.case("freebuds-6"))
    }

    @Test
    fun freebudsPro5GetsItsOwnRendersRegardlessOfShape() {
        assertEquals(R.drawable.battery_buds_freebuds_pro_5, ProductArt.battery("stem", "freebuds-pro-5"))
        assertEquals(R.drawable.thumb_buds_freebuds_pro_5, ProductArt.thumbnail("stem", "freebuds-pro-5"))
        assertEquals(R.drawable.battery_case_freebuds_pro_5, ProductArt.case("freebuds-pro-5"))
    }

    @Test
    fun modelOverrideWinsEvenWhenTheShapeDisagrees() {
        // freebuds-5 is a "round" profile; a mismatched shape argument must not change the result.
        assertEquals(R.drawable.battery_buds_freebuds_5, ProductArt.battery("stem", "freebuds-5"))
        assertEquals(R.drawable.thumb_buds_freebuds_5, ProductArt.thumbnail("stem", "freebuds-5"))
        // freebuds-pro-5 is a "stem" profile; a mismatched "round" shape argument must not change the result.
        assertEquals(R.drawable.battery_buds_freebuds_pro_5, ProductArt.battery("round", "freebuds-pro-5"))
        assertEquals(R.drawable.thumb_buds_freebuds_pro_5, ProductArt.thumbnail("round", "freebuds-pro-5"))
    }

    @Test
    fun otherModelsKeepTheShapeBasedRenderEvenWithAProfileId() {
        assertEquals(R.drawable.battery_buds_round, ProductArt.battery("round", "freebuds-4"))
        assertEquals(R.drawable.thumb_buds_round, ProductArt.thumbnail("round", "freebuds-4"))
        assertEquals(R.drawable.battery_buds_stem, ProductArt.battery("stem", "freebuds-pro-2"))
    }
}
