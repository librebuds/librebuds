// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import io.github.librebuds.overlay.ConnectionIslandSlot
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

    @Test
    fun shownOverlayPopupOfEitherStyleClosesAnOpenConnectionIsland() {
        for (style in PopupStyle.entries) {
            assertTrue("$style", popupClosesIsland(islandOpen = true, action = action(style = style), shown = true))
            assertFalse("$style", popupClosesIsland(islandOpen = false, action = action(style = style), shown = true))
        }
    }

    @Test
    fun overlayThatFailedToShowLeavesTheConnectionIsland() {
        // addView failed: the popup falls back to the notification and the island stays.
        for (style in PopupStyle.entries) {
            assertFalse("$style", popupClosesIsland(islandOpen = true, action = action(style = style), shown = false))
        }
    }

    @Test
    fun notificationOrNoPopupLeavesTheConnectionIsland() {
        assertFalse(popupClosesIsland(islandOpen = true, action = PopupAction.Notification, shown = false))
        assertFalse(popupClosesIsland(islandOpen = true, action = PopupAction.Nothing, shown = false))
    }

    @Test
    fun connectionIslandSlotClosesWhatWasRegisteredUntilUnregistered() {
        var open = true
        var closed = 0
        ConnectionIslandSlot.register(isOpen = { open }, close = { closed++; open = false })
        try {
            assertTrue(ConnectionIslandSlot.isOpen())
            if (popupClosesIsland(ConnectionIslandSlot.isOpen(), PopupAction.Island, shown = true)) ConnectionIslandSlot.close()
            assertEquals(1, closed)
            assertFalse(ConnectionIslandSlot.isOpen())
        } finally {
            ConnectionIslandSlot.unregister()
        }
        open = true
        assertFalse(ConnectionIslandSlot.isOpen())
        ConnectionIslandSlot.close()
        assertEquals(1, closed)
    }

    @Test
    fun deliveryIsDescribedForTheLog() {
        assertEquals("overlay card", deliveryDescription(PopupAction.Card, shown = true, notified = false))
        assertEquals("overlay island", deliveryDescription(PopupAction.Island, shown = true, notified = false))
        assertEquals(
            "notification (locked screen or no overlay permission)",
            deliveryDescription(PopupAction.Notification, shown = false, notified = true),
        )
        assertEquals("notification (overlay could not be added)", deliveryDescription(PopupAction.Card, shown = false, notified = true))
        assertTrue(deliveryDescription(PopupAction.Nothing, shown = false, notified = false).startsWith("not delivered"))
    }

    @Test
    fun connectionIslandWaitsAMinuteAfterThePopupOfTheSameEarbuds() {
        val shown = "freebuds-5" to 1_000L
        assertTrue(popupRecentlyShown(shown, "freebuds-5", now = 20_000))
        assertFalse(popupRecentlyShown(shown, "freebuds-5", now = 1_000 + ISLAND_AFTER_POPUP_MILLIS))
        assertFalse(popupRecentlyShown(shown, "freebuds-6", now = 20_000))
        assertFalse(popupRecentlyShown(null, "freebuds-5", now = 20_000))
        // A clock set back does not hold the island off.
        assertFalse(popupRecentlyShown(shown, "freebuds-5", now = 500))
    }
}
