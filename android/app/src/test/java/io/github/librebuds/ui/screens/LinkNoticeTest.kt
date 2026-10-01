// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

import io.github.librebuds.state.LinkState
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkNoticeTest {
    @Test
    fun aPairTheControllerDoesNotHoldShowsItsOwnBluetoothState() {
        for (link in LinkState.entries) {
            assertEquals(LinkNotice.PHONE_ONLY, linkNotice(current = false, phoneConnected = true, link = link))
            assertEquals(LinkNotice.NOT_CONNECTED, linkNotice(current = false, phoneConnected = false, link = link))
        }
    }

    @Test
    fun theControllersPairFollowsItsLink() {
        assertEquals(LinkNotice.NONE, linkNotice(current = true, phoneConnected = true, link = LinkState.CONNECTED))
        assertEquals(LinkNotice.CONNECTING, linkNotice(current = true, phoneConnected = true, link = LinkState.CONNECTING))
        assertEquals(LinkNotice.TAKEN_OVER, linkNotice(current = true, phoneConnected = true, link = LinkState.TAKEN_OVER))
    }

    @Test
    fun aDroppedLinkSaysNotConnectedOnlyWhenThePhoneLostTheEarbudsToo() {
        assertEquals(LinkNotice.NOT_CONNECTED, linkNotice(current = true, phoneConnected = false, link = LinkState.DISCONNECTED))
        // The phone still has them (ACL, A2DP or headset): never "Not connected" for earbuds in the ears.
        assertEquals(LinkNotice.PHONE_ONLY, linkNotice(current = true, phoneConnected = true, link = LinkState.DISCONNECTED))
    }
}
