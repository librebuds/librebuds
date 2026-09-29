// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceRulesTest {
    private val stored = Stored(address = "AA:BB:CC:DD:EE:FF", name = "Buds", associationId = 7)

    @Test
    fun matchingAssociationAppearedStarts() {
        assertEquals(PresenceAction.START, presenceAction(Presence.APPEARED, 7, stored))
    }

    @Test
    fun matchingAssociationDisappearedStops() {
        assertEquals(PresenceAction.STOP, presenceAction(Presence.DISAPPEARED, 7, stored))
    }

    @Test
    fun otherAssociationIsIgnored() {
        assertEquals(PresenceAction.IGNORE, presenceAction(Presence.APPEARED, 8, stored))
    }

    @Test
    fun presenceWithoutStoredDeviceIsIgnored() {
        assertEquals(PresenceAction.IGNORE, presenceAction(Presence.APPEARED, 7, null))
    }

    @Test
    fun unrelatedPresenceEventIsIgnored() {
        assertEquals(PresenceAction.IGNORE, presenceAction(null, 7, stored))
    }

    @Test
    fun aclForPrimaryMatchesCaseInsensitively() {
        assertEquals(PresenceAction.START, aclAction(Presence.APPEARED, "aa:bb:cc:dd:ee:ff", stored))
        assertEquals(PresenceAction.STOP, aclAction(Presence.DISAPPEARED, "AA:BB:CC:DD:EE:FF", stored))
    }

    @Test
    fun aclForOtherDeviceIsIgnored() {
        assertEquals(PresenceAction.IGNORE, aclAction(Presence.APPEARED, "11:22:33:44:55:66", stored))
    }

    @Test
    fun aclWithoutStoredDeviceOrAddressIsIgnored() {
        assertEquals(PresenceAction.IGNORE, aclAction(Presence.APPEARED, "AA:BB:CC:DD:EE:FF", null))
        assertEquals(PresenceAction.IGNORE, aclAction(Presence.APPEARED, null, stored))
    }

    @Test
    fun aclWithUnknownActionIsIgnored() {
        assertEquals(PresenceAction.IGNORE, aclAction(null, "AA:BB:CC:DD:EE:FF", stored))
    }
}
