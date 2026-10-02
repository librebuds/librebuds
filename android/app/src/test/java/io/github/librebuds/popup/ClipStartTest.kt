// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipStartTest {
    private var starts = 0
    private val start = ClipStart { starts++ }

    @Test
    fun `starts once both the player and the surface are ready, in either order`() {
        start.onSurface()
        assertEquals(0, starts)
        start.onPrepared()
        assertEquals(1, starts)

        val other = ClipStart { starts++ }
        other.onPrepared()
        assertEquals(1, starts)
        other.onSurface()
        assertEquals(2, starts)
    }

    @Test
    fun `starts only once`() {
        start.onPrepared()
        start.onSurface()
        start.onSurface()
        start.onPrepared()
        assertEquals(1, starts)
    }
}
