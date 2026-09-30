// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import io.github.librebuds.ui.screens.onboarding.OnboardingState
import io.github.librebuds.ui.screens.onboarding.PermissionHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {
    private val connect = "android.permission.BLUETOOTH_CONNECT"
    private val scan = "android.permission.BLUETOOTH_SCAN"
    private val notify = "android.permission.POST_NOTIFICATIONS"

    @Test
    fun listsAllFourItemsInOrder() {
        val items = OnboardingState.items(sdkInt = 37, granted = emptySet(), canDrawOverlays = false)
        assertEquals(listOf(connect, scan, notify, "overlay"), items.map { it.key })
        assertEquals(listOf(true, false, false, false), items.map { it.required })
    }

    @Test
    fun continuesWhenOnlyRequiredGranted() {
        val items = OnboardingState.items(sdkInt = 37, granted = setOf(connect), canDrawOverlays = false)
        assertTrue(OnboardingState.canContinue(items))
    }

    @Test
    fun blocksWithoutBluetoothConnect() {
        val items = OnboardingState.items(sdkInt = 37, granted = setOf(scan, notify), canDrawOverlays = true)
        assertFalse(OnboardingState.canContinue(items))
    }

    @Test
    fun reportsOverlayGrant() {
        val items = OnboardingState.items(sdkInt = 37, granted = emptySet(), canDrawOverlays = true)
        assertTrue(items.single { it.key == "overlay" }.granted)
    }

    @Test
    fun opensSettingsOnlyWhenDialogCanNoLongerBeShown() {
        // Requested before, denied, no rationale: Android will not show the dialog again.
        assertTrue(OnboardingState.shouldOpenSettings(granted = false, wasRequested = true, shouldShowRationale = false))
        // Never requested: shouldShowRationale is false too, but the dialog still appears.
        assertFalse(OnboardingState.shouldOpenSettings(granted = false, wasRequested = false, shouldShowRationale = false))
        // Denied once: the dialog can be shown again.
        assertFalse(OnboardingState.shouldOpenSettings(granted = false, wasRequested = true, shouldShowRationale = true))
        // Already granted: nothing to open.
        assertFalse(OnboardingState.shouldOpenSettings(granted = true, wasRequested = true, shouldShowRationale = false))
    }

    @Test
    fun overlayHintOnlyAfterTheUserWasSentToTheSettings() {
        // Never sent to the overlay page: nothing to explain yet.
        assertEquals(PermissionHint.NONE, OnboardingState.hintFor("overlay", granted = false, wasRequested = false))
        // Back from the overlay page without the grant: restricted settings may have blocked it.
        assertEquals(PermissionHint.RESTRICTED_SETTINGS, OnboardingState.hintFor("overlay", granted = false, wasRequested = true))
        // Granted after all: the hint goes away.
        assertEquals(PermissionHint.NONE, OnboardingState.hintFor("overlay", granted = true, wasRequested = true))
    }

    @Test
    fun overlayHintIgnoresRationale() {
        // The overlay has no dialog, so shouldShowRationale must not hide the hint.
        assertEquals(
            PermissionHint.RESTRICTED_SETTINGS,
            OnboardingState.hintFor("overlay", granted = false, wasRequested = true, shouldShowRationale = true),
        )
    }

    @Test
    fun runtimePermissionHintFollowsOpenSettingsRule() {
        // Denied without a dialog on the first request: only App info can grant it now.
        assertEquals(PermissionHint.APP_INFO, OnboardingState.hintFor(notify, granted = false, wasRequested = true, shouldShowRationale = false))
        // Never requested: the dialog still appears.
        assertEquals(PermissionHint.NONE, OnboardingState.hintFor(scan, granted = false, wasRequested = false, shouldShowRationale = false))
        // Denied once, the dialog can come back.
        assertEquals(PermissionHint.NONE, OnboardingState.hintFor(connect, granted = false, wasRequested = true, shouldShowRationale = true))
        assertEquals(PermissionHint.NONE, OnboardingState.hintFor(connect, granted = true, wasRequested = true, shouldShowRationale = false))
    }
}
