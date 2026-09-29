// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.overlay

import io.github.librebuds.ui.model.Battery

/** What the connection island needs from whoever shows it. */
interface IslandHost {
    /** Set while an island is on screen, so a second one is not stacked on top. */
    var islandOpen: Boolean

    fun batteries(): List<Battery>

    /** Takes the earbuds back from another device. */
    fun takeOver()

    fun openApp()
}
