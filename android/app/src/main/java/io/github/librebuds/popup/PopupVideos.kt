// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import io.github.librebuds.R

/**
 * Case-opening clips (LibreBuds original artwork, see NOTICE) as profile id to light/dark clip for
 * [artFor]. Each clip is 720x720 at 60 fps (interpolated from the 24 fps originals), about 3.5 s,
 * and ends on a held frame of the open case; the light clips have a #FFFFFF background and the dark
 * ones #1C1C1E, the colours of `popup_video_background`.
 * Profiles without a clip get the vector drawing of their shape.
 */
object PopupVideos {
    val clips: Map<String, Pair<Int, Int>> = mapOf(
        "freebuds-4" to (R.raw.popup_freebuds_4_light to R.raw.popup_freebuds_4_dark),
        "freebuds-5" to (R.raw.popup_freebuds_5_light to R.raw.popup_freebuds_5_dark),
        "freebuds-6" to (R.raw.popup_freebuds_6_light to R.raw.popup_freebuds_6_dark),
        "freebuds-pro-2" to (R.raw.popup_freebuds_pro_2_light to R.raw.popup_freebuds_pro_2_dark),
        "freebuds-pro-3" to (R.raw.popup_freebuds_pro_3_light to R.raw.popup_freebuds_pro_3_dark),
        "freebuds-pro-4" to (R.raw.popup_freebuds_pro_4_light to R.raw.popup_freebuds_pro_4_dark),
        "freebuds-pro-5" to (R.raw.popup_freebuds_pro_5_light to R.raw.popup_freebuds_pro_5_dark),
    )
}
