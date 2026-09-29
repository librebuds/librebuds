// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import io.github.librebuds.protocol.profile.ProfileRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoPopupTest {
    @Test
    fun demoBeaconParsesToTheDocumentedValues() {
        val beacon = demoBeacon()
        assertNotNull(beacon)
        assertTrue(beacon!!.closeRangeEnabled)
        assertEquals("000155", beacon.modelId)
        assertEquals(-70, beacon.referenceRssi)
        assertEquals(100, beacon.left?.percent)
        assertTrue(beacon.left?.charging ?: false)
        assertEquals(80, beacon.right?.percent)
        assertFalse(beacon.right?.charging ?: true)
        assertEquals(48, beacon.case?.percent)
        assertFalse(beacon.case?.charging ?: true)
    }

    @Test
    fun demoPopupModelMatchesTheRegisteredProfile() {
        val registry = ProfileRegistry.fromJson(listOf("""{"id":"freebuds-6","name":"FreeBuds 6","art":"round","match":{"modelId":["000155"]}}"""))
        val model = demoPopupModel(registry)
        assertNotNull(model)
        assertEquals("FreeBuds 6", model!!.title)
        assertEquals("round", model.art)
        assertEquals("freebuds-6", model.profileId)
    }

    @Test
    fun demoPopupModelFallsBackToGenericWhenUnmatched() {
        val model = demoPopupModel(ProfileRegistry(emptyList()))
        assertNotNull(model)
        assertEquals(ProfileRegistry.GENERIC.id, model!!.profileId)
    }
}
