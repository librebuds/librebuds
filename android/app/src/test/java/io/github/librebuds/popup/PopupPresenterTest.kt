// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import org.junit.Assert.assertEquals
import org.junit.Test

class PopupPresenterTest {
    @Test
    fun overlayWhenAllowed() =
        assertEquals(PopupAction.Overlay, popupAction(enabled = true, canDrawOverlays = true, notificationsAllowed = true, locked = false))

    @Test
    fun notificationWithoutOverlayPermission() =
        assertEquals(PopupAction.Notification, popupAction(enabled = true, canDrawOverlays = false, notificationsAllowed = true, locked = false))

    @Test
    fun nothingWhenDisabledOrNoWayToShow() {
        assertEquals(PopupAction.Nothing, popupAction(enabled = false, canDrawOverlays = true, notificationsAllowed = true, locked = false))
        assertEquals(PopupAction.Nothing, popupAction(enabled = true, canDrawOverlays = false, notificationsAllowed = false, locked = false))
    }

    @Test
    fun notificationOnLockedScreen() {
        assertEquals(PopupAction.Notification, popupAction(enabled = true, canDrawOverlays = true, notificationsAllowed = true, locked = true))
        // Locked without notifications: the overlay would stay behind the keyguard, so nothing.
        assertEquals(PopupAction.Nothing, popupAction(enabled = true, canDrawOverlays = true, notificationsAllowed = false, locked = true))
        assertEquals(PopupAction.Nothing, popupAction(enabled = false, canDrawOverlays = true, notificationsAllowed = true, locked = true))
    }
}
