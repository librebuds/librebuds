// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import io.github.librebuds.R

/**
 * Case-opening clips as profile id to light/dark clip for [artFor]. Each clip is 720x720 at 60 fps,
 * 2 to 4 s, and ends on a held frame of the open case; the light clips have a #FFFFFF background and
 * the dark ones about #1D1C1F (the card takes the colour of the first decoded frame).
 * Profiles without a clip get the vector drawing of their shape.
 */
object PopupVideos {
    val clips: Map<String, Pair<Int, Int>> = mapOf(
        "freebuds-3" to (R.raw.popup_freebuds_3_light to R.raw.popup_freebuds_3_dark),
        "freebuds-4" to (R.raw.popup_freebuds_4_light to R.raw.popup_freebuds_4_dark),
        "freebuds-5" to (R.raw.popup_freebuds_5_light to R.raw.popup_freebuds_5_dark),
        "freebuds-6" to (R.raw.popup_freebuds_6_light to R.raw.popup_freebuds_6_dark),
        "freebuds-pro-2" to (R.raw.popup_freebuds_pro_2_light to R.raw.popup_freebuds_pro_2_dark),
        "freebuds-pro-3" to (R.raw.popup_freebuds_pro_3_light to R.raw.popup_freebuds_pro_3_dark),
        "freebuds-pro-4" to (R.raw.popup_freebuds_pro_4_light to R.raw.popup_freebuds_pro_4_dark),
        "freebuds-pro-5" to (R.raw.popup_freebuds_pro_5_light to R.raw.popup_freebuds_pro_5_dark),
    )
}
