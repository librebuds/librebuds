// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

/**
 * Release builds ship no popup clips (they are a debug-only comparison, see src/debug), so [artFor]
 * always picks the vector animation.
 */
object PopupVideos {
    fun map(): Map<String, Pair<Int, Int>> = emptyMap()
}
