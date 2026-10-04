// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.components

import io.github.librebuds.R
import io.github.librebuds.ui.model.BatteryPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProductArtTest {
    @Test
    fun stemShapeGetsStemRenders() {
        assertEquals(R.drawable.battery_bud_left_stem, ProductArt.bud(true, "stem"))
        assertEquals(R.drawable.battery_bud_right_stem, ProductArt.bud(false, "stem"))
        assertEquals(R.drawable.thumb_buds_stem, ProductArt.thumbnail("stem"))
    }

    @Test
    fun roundGenericAndUnknownShapesFallBackToRoundRenders() {
        for (shape in listOf("round", "generic", "eyewear", "")) {
            assertEquals(R.drawable.battery_bud_left_round, ProductArt.bud(true, shape))
        assertEquals(R.drawable.battery_bud_right_round, ProductArt.bud(false, shape))
            assertEquals(R.drawable.thumb_buds_round, ProductArt.thumbnail(shape))
        }
    }

    @Test
    fun noProfileIdFallsBackToShapeForCaseAsWell() {
        assertEquals(R.drawable.battery_case, ProductArt.case())
        assertEquals(R.drawable.battery_case, ProductArt.case(null))
        assertEquals(R.drawable.battery_case, ProductArt.case("generic"))
        assertEquals(R.drawable.battery_case, ProductArt.case("freebuds-3"))
    }

    @Test
    fun freebuds5GetsItsOwnRendersRegardlessOfShape() {
        assertEquals(R.drawable.battery_bud_left_freebuds_5, ProductArt.bud(true, "round", "freebuds-5"))
        assertEquals(R.drawable.battery_bud_right_freebuds_5, ProductArt.bud(false, "round", "freebuds-5"))
        assertEquals(R.drawable.thumb_buds_freebuds_5, ProductArt.thumbnail("round", "freebuds-5"))
        assertEquals(R.drawable.battery_case_freebuds_5, ProductArt.case("freebuds-5"))
    }

    @Test
    fun freebuds6GetsItsOwnRendersRegardlessOfShape() {
        assertEquals(R.drawable.battery_bud_left_freebuds_6, ProductArt.bud(true, "round", "freebuds-6"))
        assertEquals(R.drawable.battery_bud_right_freebuds_6, ProductArt.bud(false, "round", "freebuds-6"))
        assertEquals(R.drawable.thumb_buds_freebuds_6, ProductArt.thumbnail("round", "freebuds-6"))
        assertEquals(R.drawable.battery_case_freebuds_6, ProductArt.case("freebuds-6"))
    }

    @Test
    fun freebudsPro5GetsItsOwnRendersRegardlessOfShape() {
        assertEquals(R.drawable.battery_bud_left_freebuds_pro_5, ProductArt.bud(true, "stem", "freebuds-pro-5"))
        assertEquals(R.drawable.battery_bud_right_freebuds_pro_5, ProductArt.bud(false, "stem", "freebuds-pro-5"))
        assertEquals(R.drawable.thumb_buds_freebuds_pro_5, ProductArt.thumbnail("stem", "freebuds-pro-5"))
        assertEquals(R.drawable.battery_case_freebuds_pro_5, ProductArt.case("freebuds-pro-5"))
    }

    @Test
    fun modelOverrideWinsEvenWhenTheShapeDisagrees() {
        // freebuds-5 is a "round" profile; a mismatched shape argument must not change the result.
        assertEquals(R.drawable.battery_bud_left_freebuds_5, ProductArt.bud(true, "stem", "freebuds-5"))
        assertEquals(R.drawable.battery_bud_right_freebuds_5, ProductArt.bud(false, "stem", "freebuds-5"))
        assertEquals(R.drawable.thumb_buds_freebuds_5, ProductArt.thumbnail("stem", "freebuds-5"))
        // freebuds-pro-5 is a "stem" profile; a mismatched "round" shape argument must not change the result.
        assertEquals(R.drawable.battery_bud_left_freebuds_pro_5, ProductArt.bud(true, "round", "freebuds-pro-5"))
        assertEquals(R.drawable.battery_bud_right_freebuds_pro_5, ProductArt.bud(false, "round", "freebuds-pro-5"))
        assertEquals(R.drawable.thumb_buds_freebuds_pro_5, ProductArt.thumbnail("round", "freebuds-pro-5"))
    }

    @Test
    fun otherModelsKeepTheShapeBasedRenderEvenWithAProfileId() {
        assertEquals(R.drawable.battery_bud_left_round, ProductArt.bud(true, "round", "freebuds-3"))
        assertEquals(R.drawable.battery_bud_right_round, ProductArt.bud(false, "round", "freebuds-3"))
        assertEquals(R.drawable.thumb_buds_round, ProductArt.thumbnail("round", "freebuds-3"))
        assertEquals(R.drawable.battery_bud_left_stem, ProductArt.bud(true, "stem", "freebuds-pro-6"))
        assertEquals(R.drawable.battery_bud_right_stem, ProductArt.bud(false, "stem", "freebuds-pro-6"))
    }

    @Test
    fun proModelsGetTheirOwnRendersAndPro2SharesWithPro3() {
        for (id in listOf("freebuds-pro-2", "freebuds-pro-3")) {
            assertEquals(id, R.drawable.battery_bud_left_freebuds_pro_2, ProductArt.bud(true, "stem", id))
            assertEquals(id, R.drawable.battery_bud_right_freebuds_pro_2, ProductArt.bud(false, "stem", id))
            assertEquals(id, R.drawable.thumb_buds_freebuds_pro_2, ProductArt.thumbnail("stem", id))
            assertEquals(id, R.drawable.battery_case_freebuds_pro_2, ProductArt.case(id))
        }
        assertEquals(R.drawable.battery_bud_left_freebuds_pro_4, ProductArt.bud(true, "stem", "freebuds-pro-4"))
        assertEquals(R.drawable.battery_bud_right_freebuds_pro_4, ProductArt.bud(false, "stem", "freebuds-pro-4"))
        assertEquals(R.drawable.thumb_buds_freebuds_pro_4, ProductArt.thumbnail("stem", "freebuds-pro-4"))
        assertEquals(R.drawable.battery_case_freebuds_pro_4, ProductArt.case("freebuds-pro-4"))
    }

    @Test
    fun partPicksLeftRightAndCase() {
        assertEquals(R.drawable.battery_bud_left_freebuds_6, ProductArt.part(BatteryPart.LEFT, "round", "freebuds-6"))
        assertEquals(R.drawable.battery_bud_right_freebuds_6, ProductArt.part(BatteryPart.RIGHT, "round", "freebuds-6"))
        assertEquals(R.drawable.battery_case_freebuds_6, ProductArt.part(BatteryPart.CASE, "round", "freebuds-6"))
        assertEquals(R.drawable.battery_bud_left_stem, ProductArt.part(BatteryPart.LEFT, "stem"))
        assertEquals(R.drawable.battery_bud_right_round, ProductArt.part(BatteryPart.RIGHT, "generic"))
        assertEquals(R.drawable.battery_case, ProductArt.part(BatteryPart.CASE, "stem", "freebuds-3"))
        assertEquals(R.drawable.battery_case_freebuds_4, ProductArt.part(BatteryPart.CASE, "stem", "freebuds-4"))
    }

    @Test
    fun leftAndRightBudsAreDifferentPictures() {
        for (profile in listOf(null, "freebuds-5", "freebuds-6", "freebuds-pro-5")) {
            for (shape in listOf("round", "stem")) {
                assertNotEquals(ProductArt.bud(true, shape, profile), ProductArt.bud(false, shape, profile))
            }
        }
    }
}
