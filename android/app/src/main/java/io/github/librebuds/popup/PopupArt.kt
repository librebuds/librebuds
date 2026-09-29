// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import io.github.librebuds.R

/** Popup artwork variants: A = our animated vector drawings, B = generated video clips. */
enum class ArtVariant { VECTOR, VIDEO }

/** What the popup shows in its art slot; video resources are null when the variant has none. */
data class PopupArt(val variant: ArtVariant, val avdRes: Int, val videoLight: Int?, val videoDark: Int?)

/**
 * Picks the artwork for a profile's [shape]. For now every profile gets the generic earbuds drawing;
 * the per-shape drawings and clips replace this body later without changing the signature.
 */
@Suppress("UNUSED_PARAMETER")
fun artFor(shape: String, variant: ArtVariant, videos: Map<String, Pair<Int, Int>>): PopupArt =
    PopupArt(ArtVariant.VECTOR, R.drawable.ic_buds, null, null)
