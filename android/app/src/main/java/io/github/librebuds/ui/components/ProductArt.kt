// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.components

import androidx.annotation.DrawableRes
import io.github.librebuds.R

/**
 * Product renders. Most profiles get art picked by their `art` shape: stem buds for "stem", round
 * buds for anything else (including "generic" and unknown shapes). A few profiles instead get their
 * own render, keyed by profile id in [MODEL_OVERRIDES]; the shape is then ignored for that profile.
 * [battery], [thumbnail] and [case] all fall back to the shape-based render when [profileId] is null
 * or has no entry in [MODEL_OVERRIDES].
 */
object ProductArt {
    private data class ModelArt(
        @param:DrawableRes val battery: Int,
        @param:DrawableRes val thumbnail: Int,
        @param:DrawableRes val case: Int,
    )

    /** Profile id to its own renders, for the profiles that get a model-specific product shot. */
    private val MODEL_OVERRIDES: Map<String, ModelArt> = mapOf(
        "freebuds-5" to ModelArt(
            battery = R.drawable.battery_buds_freebuds_5,
            thumbnail = R.drawable.thumb_buds_freebuds_5,
            case = R.drawable.battery_case_freebuds_5,
        ),
        "freebuds-6" to ModelArt(
            battery = R.drawable.battery_buds_freebuds_6,
            thumbnail = R.drawable.thumb_buds_freebuds_6,
            case = R.drawable.battery_case_freebuds_6,
        ),
    )

    /** Earbuds above the battery rings, the same canvas and baseline as [case]. */
    @DrawableRes
    fun battery(shape: String, profileId: String? = null): Int =
        profileId?.let { MODEL_OVERRIDES[it]?.battery }
            ?: if (shape == "stem") R.drawable.battery_buds_stem else R.drawable.battery_buds_round

    /** Small earbuds for a pair switcher row, cropped tight for a tile. */
    @DrawableRes
    fun thumbnail(shape: String, profileId: String? = null): Int =
        profileId?.let { MODEL_OVERRIDES[it]?.thumbnail }
            ?: if (shape == "stem") R.drawable.thumb_buds_stem else R.drawable.thumb_buds_round

    /** The case, the same canvas and baseline as [battery]. Shapeless: only [profileId] can override it. */
    @DrawableRes
    fun case(profileId: String? = null): Int =
        profileId?.let { MODEL_OVERRIDES[it]?.case } ?: R.drawable.battery_case
}
