// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import io.github.librebuds.overlay.islandShouldShow
import io.github.librebuds.state.LinkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupPresenterTest {
    private fun action(
        style: PopupStyle = PopupStyle.ISLAND,
        enabled: Boolean = true,
        overlay: Boolean = true,
        notifications: Boolean = true,
        locked: Boolean = false,
    ) = popupAction(enabled = enabled, style = style, canDrawOverlays = overlay, notificationsAllowed = notifications, locked = locked)

    @Test
    fun overlayWhenAllowedFollowsTheStyle() {
        assertEquals(PopupAction.Island, action(style = PopupStyle.ISLAND))
        assertEquals(PopupAction.Card, action(style = PopupStyle.CARD))
    }

    @Test
    fun notificationWithoutOverlayPermission() {
        for (style in PopupStyle.entries) {
            assertEquals(PopupAction.Notification, action(style = style, overlay = false))
        }
    }

    @Test
    fun nothingWhenDisabledOrNoWayToShow() {
        for (style in PopupStyle.entries) {
            assertEquals(PopupAction.Nothing, action(style = style, enabled = false))
            assertEquals(PopupAction.Nothing, action(style = style, overlay = false, notifications = false))
        }
    }

    @Test
    fun notificationOnLockedScreen() {
        for (style in PopupStyle.entries) {
            assertEquals(PopupAction.Notification, action(style = style, locked = true))
            // Locked without notifications: the overlay would stay behind the keyguard, so nothing.
            assertEquals(PopupAction.Nothing, action(style = style, notifications = false, locked = true))
            assertEquals(PopupAction.Nothing, action(style = style, enabled = false, locked = true))
        }
    }

    /** Stands in for the card or the island; [style] only labels which one. */
    private class FakePopup(val style: PopupStyle, override val profileId: String?, override var isOpen: Boolean = true) : CasePopup {
        override fun close() {
            isOpen = false
        }
    }

    private fun connectIsland(popup: CasePopup?, profileId: String = "freebuds-6") = islandShouldShow(
        previous = LinkState.CONNECTING,
        current = LinkState.CONNECTED,
        enabled = true,
        canDrawOverlays = true,
        popupShowing = popupBlocksIsland(popup, profileId),
    )

    @Test
    fun openPopupOfEitherStyleKeepsTheConnectionIslandAway() {
        for (style in PopupStyle.entries) {
            assertFalse("$style", connectIsland(FakePopup(style, "freebuds-6")))
        }
    }

    @Test
    fun connectionIslandShowsOncePopupClosed() {
        for (style in PopupStyle.entries) {
            val popup = FakePopup(style, "freebuds-6")
            popup.close()
            assertTrue("$style", connectIsland(popup))
        }
        assertTrue(connectIsland(null))
    }

    @Test
    fun popupForOtherEarbudsDoesNotBlockTheIsland() {
        for (style in PopupStyle.entries) {
            assertTrue("$style", connectIsland(FakePopup(style, "freebuds-pro-4")))
            assertFalse("$style", popupBlocksIsland(FakePopup(style, null), "freebuds-6"))
        }
    }
}
