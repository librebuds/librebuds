// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import io.github.librebuds.R

/** Popup artwork variants: A = our animated vector drawings, B = generated video clips. */
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
 * Picks the artwork for a profile's `art` [shape]. Unknown shapes get the generic drawing, and
 * [ArtVariant.VIDEO] falls back to [ArtVariant.VECTOR] when [videos] (shape to light/dark clip) has
 * no clip for the shape.
 */
fun artFor(shape: String, variant: ArtVariant, videos: Map<String, Pair<Int, Int>>): PopupArt {
    val known = if (shape in animations) shape else "generic"
    val avd = animations.getValue(known)
    val clip = videos[known]
    return if (variant == ArtVariant.VIDEO && clip != null) {
        PopupArt(ArtVariant.VIDEO, avd, clip.first, clip.second)
    } else {
        PopupArt(ArtVariant.VECTOR, avd, null, null)
    }
}

