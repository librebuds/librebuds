// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.components

import androidx.annotation.DrawableRes
import io.github.librebuds.R
import io.github.librebuds.ui.model.BatteryPart

/**
 * Product renders. Most profiles get art picked by their `art` shape: stem buds for "stem", round
 * buds for anything else (including "generic" and unknown shapes). A few profiles instead get their
 * own render, keyed by profile id in [MODEL_OVERRIDES]; the shape is then ignored for that profile.
 * [bud], [part], [thumbnail] and [case] all fall back to the shape-based render when [profileId] is
 * null or has no entry in [MODEL_OVERRIDES].
 *
 * The battery view art (left bud, right bud, case) shares one canvas and one bottom baseline, so the
 * three columns line up whatever the model. Each product is fitted to the canvas on its own, so the
 * buds are not drawn at their true size relative to the case.
 */
object ProductArt {
    private data class ModelArt(
        @param:DrawableRes val left: Int,
        @param:DrawableRes val right: Int,
        @param:DrawableRes val thumbnail: Int,
        @param:DrawableRes val case: Int,
    )

    /** Profile id to its own renders, for the profiles that get a model-specific product shot. */
    private val MODEL_OVERRIDES: Map<String, ModelArt> = mapOf(
        "freebuds-3" to ModelArt(
            left = R.drawable.battery_bud_left_freebuds_3,
            right = R.drawable.battery_bud_right_freebuds_3,
            thumbnail = R.drawable.thumb_buds_freebuds_3,
            case = R.drawable.battery_case_freebuds_3,
        ),
        "freebuds-4" to ModelArt(
            left = R.drawable.battery_bud_left_freebuds_4,
            right = R.drawable.battery_bud_right_freebuds_4,
            thumbnail = R.drawable.thumb_buds_freebuds_4,
            case = R.drawable.battery_case_freebuds_4,
        ),
        "freebuds-5" to ModelArt(
            left = R.drawable.battery_bud_left_freebuds_5,
            right = R.drawable.battery_bud_right_freebuds_5,
            thumbnail = R.drawable.thumb_buds_freebuds_5,
            case = R.drawable.battery_case_freebuds_5,
        ),
        "freebuds-6" to ModelArt(
            left = R.drawable.battery_bud_left_freebuds_6,
            right = R.drawable.battery_bud_right_freebuds_6,
            thumbnail = R.drawable.thumb_buds_freebuds_6,
            case = R.drawable.battery_case_freebuds_6,
        ),
        "freebuds-pro-5" to ModelArt(
            left = R.drawable.battery_bud_left_freebuds_pro_5,
            right = R.drawable.battery_bud_right_freebuds_pro_5,
            thumbnail = R.drawable.thumb_buds_freebuds_pro_5,
            case = R.drawable.battery_case_freebuds_pro_5,
        ),
        // FreeBuds 6i intentionally reuses the FreeBuds Pro 5 artwork until dedicated 6i art is added.
        "freebuds-6i" to ModelArt(
            left = R.drawable.battery_bud_left_freebuds_pro_5,
            right = R.drawable.battery_bud_right_freebuds_pro_5,
            thumbnail = R.drawable.thumb_buds_freebuds_pro_5,
            case = R.drawable.battery_case_freebuds_pro_5,
        ),
        "freebuds-pro-2" to ModelArt(
            left = R.drawable.battery_bud_left_freebuds_pro_2,
            right = R.drawable.battery_bud_right_freebuds_pro_2,
            thumbnail = R.drawable.thumb_buds_freebuds_pro_2,
            case = R.drawable.battery_case_freebuds_pro_2,
        ),
        "freebuds-pro-3" to ModelArt(
            left = R.drawable.battery_bud_left_freebuds_pro_3,
            right = R.drawable.battery_bud_right_freebuds_pro_3,
            thumbnail = R.drawable.thumb_buds_freebuds_pro_3,
            case = R.drawable.battery_case_freebuds_pro_3,
        ),
        "freebuds-pro-4" to ModelArt(
            left = R.drawable.battery_bud_left_freebuds_pro_4,
            right = R.drawable.battery_bud_right_freebuds_pro_4,
            thumbnail = R.drawable.thumb_buds_freebuds_pro_4,
            case = R.drawable.battery_case_freebuds_pro_4,
        ),
        // Exact-model SVG illustrations adapted from PairFree (GPLv3); see NOTICE.
        // Unknown models intentionally keep generic artwork.
        "pairfree-samsung-buds3pro" to ModelArt(
            left = R.drawable.pairfree_samsung_buds3pro_bud,
            right = R.drawable.pairfree_samsung_buds3pro_bud,
            thumbnail = R.drawable.pairfree_samsung_buds3pro_bud,
            case = R.drawable.battery_case,
        ),
        "pairfree-sony-wfxm5" to ModelArt(
            left = R.drawable.pairfree_sony_wfxm5_bud,
            right = R.drawable.pairfree_sony_wfxm5_bud,
            thumbnail = R.drawable.pairfree_sony_wfxm5_bud,
            case = R.drawable.battery_case,
        ),
        "pairfree-soundcore-a30i" to ModelArt(
            left = R.drawable.pairfree_soundcore_a30i_bud,
            right = R.drawable.pairfree_soundcore_a30i_bud,
            thumbnail = R.drawable.pairfree_soundcore_a30i_bud,
            case = R.drawable.pairfree_soundcore_a30i_case,
        ),
        "pairfree-nothing-ear" to ModelArt(
            left = R.drawable.pairfree_nothing_ear_bud,
            right = R.drawable.pairfree_nothing_ear_bud,
            thumbnail = R.drawable.pairfree_nothing_ear_bud,
            case = R.drawable.battery_case,
        ),
        "pairfree-apple-airpods-pro" to ModelArt(
            left = R.drawable.pairfree_apple_airpods_pro_bud,
            right = R.drawable.pairfree_apple_airpods_pro_bud,
            thumbnail = R.drawable.pairfree_apple_airpods_pro_bud,
            case = R.drawable.pairfree_apple_airpods_pro_case,
        ),
    )

    /**
     * Clearly identified near-identical variants can share a family render.
     * These are illustrations, not exact colour/material matches for a particular SKU.
     * FreeClip and neckband designs deliberately do NOT map to TWS case artwork.
     */
    private val ART_FAMILY_ALIAS: Map<String, String> = mapOf(
        "freebuds-4e" to "freebuds-4",
        "freebuds-4e-v2" to "freebuds-4",
        "freebuds-pro-2-plus" to "freebuds-pro-2",
    )

    /** Models with stems must not receive the round-bean generic illustration. */
    private val STEM_SHAPE_FALLBACK: Set<String> = setOf(
        "freebuds-3i", "freebuds-4i", "freebuds-5i", "freebuds-7i",
        "freebuds-lite", "freebuds-neo", "freebuds-se", "freebuds-se-2",
        "freebuds-se-3", "freebuds-se-4-anc", "freebuds-se-5-max",
        "freebuds-pro", "freebuds-lipstick", "freebuds-lipstick-2"
    )

    private fun artwork(profileId: String?): ModelArt? =
        profileId?.let { MODEL_OVERRIDES[ART_FAMILY_ALIAS[it] ?: it] }

    private fun illustrationShape(shape: String, profileId: String?): String =
        if (profileId in STEM_SHAPE_FALLBACK) "stem" else shape

    /** One earbud above its battery ring, the same canvas and baseline as [case]. */
    @DrawableRes
    fun bud(left: Boolean, shape: String, profileId: String? = null): Int {
        val model = artwork(profileId)
        if (model != null) return if (left) model.left else model.right
        val actualShape = illustrationShape(shape, profileId)
        return when {
            actualShape == "stem" && left -> R.drawable.battery_bud_left_stem
            actualShape == "stem" -> R.drawable.battery_bud_right_stem
            left -> R.drawable.battery_bud_left_round
            else -> R.drawable.battery_bud_right_round
        }
    }

    /** The picture for one slot of the battery view. */
    @DrawableRes
    fun part(part: BatteryPart, shape: String, profileId: String? = null): Int = when (part) {
        BatteryPart.LEFT -> bud(true, shape, profileId)
        BatteryPart.RIGHT -> bud(false, shape, profileId)
        BatteryPart.CASE -> case(profileId)
    }

    /** Small earbuds for a pair switcher row, cropped tight for a tile. */
    @DrawableRes
    fun thumbnail(shape: String, profileId: String? = null): Int =
        artwork(profileId)?.thumbnail
            ?: if (illustrationShape(shape, profileId) == "stem") R.drawable.thumb_buds_stem else R.drawable.thumb_buds_round

    /** The case, the same canvas and baseline as [bud]. Shapeless: only [profileId] can override it. */
    @DrawableRes
    fun case(profileId: String? = null): Int =
        artwork(profileId)?.case ?: R.drawable.battery_case
}
