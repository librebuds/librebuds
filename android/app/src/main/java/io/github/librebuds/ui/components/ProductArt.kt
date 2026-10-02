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
    )

    /** One earbud above its battery ring, the same canvas and baseline as [case]. */
    @DrawableRes
    fun bud(left: Boolean, shape: String, profileId: String? = null): Int {
        val model = profileId?.let { MODEL_OVERRIDES[it] }
        if (model != null) return if (left) model.left else model.right
        return when {
            shape == "stem" && left -> R.drawable.battery_bud_left_stem
            shape == "stem" -> R.drawable.battery_bud_right_stem
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
        profileId?.let { MODEL_OVERRIDES[it]?.thumbnail }
            ?: if (shape == "stem") R.drawable.thumb_buds_stem else R.drawable.thumb_buds_round

    /** The case, the same canvas and baseline as [bud]. Shapeless: only [profileId] can override it. */
    @DrawableRes
    fun case(profileId: String? = null): Int =
        profileId?.let { MODEL_OVERRIDES[it]?.case } ?: R.drawable.battery_case
}
