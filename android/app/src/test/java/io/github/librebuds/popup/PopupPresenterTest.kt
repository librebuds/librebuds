// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import org.junit.Assert.assertEquals
import org.junit.Test

class PopupPresenterTest {
    @Test
    fun overlayWhenAllowed() = assertEquals(PopupAction.Overlay, popupAction(enabled = true, canDrawOverlays = true, notificationsAllowed = true))

    @Test
    fun notificationWithoutOverlayPermission() = assertEquals(PopupAction.Notification, popupAction(enabled = true, canDrawOverlays = false, notificationsAllowed = true))

    @Test
    fun nothingWhenDisabledOrNoWayToShow() {
        assertEquals(PopupAction.Nothing, popupAction(enabled = false, canDrawOverlays = true, notificationsAllowed = true))
        assertEquals(PopupAction.Nothing, popupAction(enabled = true, canDrawOverlays = false, notificationsAllowed = false))
    }
}
