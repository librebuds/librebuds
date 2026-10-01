// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.components

import androidx.annotation.DrawableRes
import io.github.librebuds.R

/**
 * Product renders picked by a profile's `art` shape: stem buds for "stem", round buds for anything
 * else (including "generic" and unknown shapes).
 */
object ProductArt {
    /** Earbuds above the battery rings, the same canvas and baseline as [CASE]. */
    @DrawableRes
    fun battery(shape: String): Int = if (shape == "stem") R.drawable.battery_buds_stem else R.drawable.battery_buds_round

    /** Small earbuds for a home list row, cropped tight for a tile. */
    @DrawableRes
    fun thumbnail(shape: String): Int = if (shape == "stem") R.drawable.thumb_buds_stem else R.drawable.thumb_buds_round

    @DrawableRes
    val CASE: Int = R.drawable.battery_case
}
