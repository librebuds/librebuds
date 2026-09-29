// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import android.bluetooth.le.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanCallbackTypeTest {
    @Test
    fun allMatchesByDefault() {
        // The lid-open change must be delivered even if the case advertised before it opened.
        assertEquals(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, scanCallbackType(offloadedFiltering = true, firstMatchExperiment = false))
        assertEquals(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, scanCallbackType(offloadedFiltering = false, firstMatchExperiment = false))
    }

    @Test
    fun firstMatchOnlyForTheExperimentWithOffloadedFiltering() {
        assertEquals(
            ScanSettings.CALLBACK_TYPE_FIRST_MATCH or ScanSettings.CALLBACK_TYPE_MATCH_LOST,
            scanCallbackType(offloadedFiltering = true, firstMatchExperiment = true),
        )
        assertEquals(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, scanCallbackType(offloadedFiltering = false, firstMatchExperiment = true))
    }

    @Test
    fun onlyMatchLostIsIgnored() {
        assertTrue(isMatchLost(ScanSettings.CALLBACK_TYPE_MATCH_LOST))
        assertFalse(isMatchLost(ScanSettings.CALLBACK_TYPE_FIRST_MATCH))
        assertFalse(isMatchLost(ScanSettings.CALLBACK_TYPE_ALL_MATCHES))
    }
}
