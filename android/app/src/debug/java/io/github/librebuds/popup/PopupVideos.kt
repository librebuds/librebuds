// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import io.github.librebuds.R

/**
 * The generated clips (CC BY-SA 4.0, see NOTICE) as shape to light/dark clip for [artFor]. Debug builds
 * only: the clips live in src/debug/res/raw for comparing the artwork variants; release builds use the
 * vector animation. See art/prompts.md for how they were made.
 */
object PopupVideos {
    fun map(): Map<String, Pair<Int, Int>> = mapOf(
        "round" to (R.raw.popup_round_light to R.raw.popup_round_dark),
    )
}
