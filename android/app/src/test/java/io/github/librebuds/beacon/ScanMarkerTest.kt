// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanMarkerTest {
    private val current = ScanMarker(bootCount = 7, versionCode = 3)

    @Test
    fun startsWhenNeverRegistered() = assertTrue(scanStartNeeded(enabled = true, registered = null, current = current, intentAlive = false))

    @Test
    fun skipsWhenRegisteredThisBootAndVersion() = assertFalse(scanStartNeeded(enabled = true, registered = current, current = current, intentAlive = true))

    @Test
    fun startsAfterRebootOrUpdate() {
        assertTrue(scanStartNeeded(true, ScanMarker(6, 3), current, intentAlive = true))
        assertTrue(scanStartNeeded(true, ScanMarker(7, 2), current, intentAlive = true))
    }

    @Test
    fun startsWhenTheResultIntentIsGone() = assertTrue(scanStartNeeded(true, current, current, intentAlive = false))

    @Test
    fun neverStartsWhenDisabled() = assertFalse(scanStartNeeded(enabled = false, registered = null, current = current, intentAlive = false))

    @Test
    fun markerTextRoundTrip() {
        assertEquals(current, ScanMarker.decode(current.encode()))
        assertNull(ScanMarker.decode("garbage"))
        assertNull(ScanMarker.decode(null))
    }
}
