// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import io.github.librebuds.R

/** What the card popup draws: our animated vector drawing, or the model's case-opening clip. */
enum class ArtVariant { VECTOR, VIDEO }

/**
 * What the popup shows in its art slot. [avdRes] is always set so a clip that fails to play can
 * fall back to the drawing; the video resources are null for [ArtVariant.VECTOR].
 */
data class PopupArt(val variant: ArtVariant, val avdRes: Int, val videoLight: Int?, val videoDark: Int?)

private val animations = mapOf(
    "stem" to R.drawable.avd_stem_open,
    "round" to R.drawable.avd_round_open,
    "generic" to R.drawable.avd_generic_open,
)

/**
 * Picks the artwork for a profile: its clip from [videos] (profile id to light/dark clip) when it has
 * one, otherwise the vector drawing of its `art` [shape]. Unknown shapes get the generic drawing.
 */
fun artFor(profileId: String, shape: String, videos: Map<String, Pair<Int, Int>> = PopupVideos.clips): PopupArt {
    val avd = animations[shape] ?: animations.getValue("generic")
    val clip = videos[profileId]
    return if (clip != null) {
        PopupArt(ArtVariant.VIDEO, avd, clip.first, clip.second)
    } else {
        PopupArt(ArtVariant.VECTOR, avd, null, null)
    }
}

/** How far (per RGB channel) a sampled clip background may be from the nominal one and still be used. */
const val CLIP_BACKGROUND_TOLERANCE = 40

/**
 * The card colour behind a playing clip. Video decoders do not all convert the clip's background to
 * exactly the colour it was made with (on the Android emulator the dark #1C1C1E came out as #2E2C30
 * through VideoView and #1E1C20 through a TextureView), so the
 * card takes the [sampled] corner colour of the first rendered frame and matches whatever the
 * decoder produced. A sample further than [CLIP_BACKGROUND_TOLERANCE] from [nominal] in any channel
 * is not background, so the card keeps [nominal]. Both colours are ARGB; the result is opaque.
 */
fun cardColorFor(nominal: Int, sampled: Int?): Int {
    if (sampled == null) return nominal
    val close = listOf(16, 8, 0).all { shift ->
        kotlin.math.abs((nominal shr shift and 0xFF) - (sampled shr shift and 0xFF)) <= CLIP_BACKGROUND_TOLERANCE
    }
    return if (close) sampled or 0xFF000000.toInt() else nominal
}
