// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens.onboarding

data class PermissionItem(val key: String, val granted: Boolean, val required: Boolean)

/** Which permissions the app asks for. Only Bluetooth connect is required; the rest degrade features. */
object OnboardingState {
    const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val OVERLAY = "overlay"

    // sdkInt is kept for future per-version rules; with minSdk 33 all four items apply today.
    fun items(sdkInt: Int, granted: Set<String>, canDrawOverlays: Boolean): List<PermissionItem> = listOf(
        PermissionItem(BLUETOOTH_CONNECT, BLUETOOTH_CONNECT in granted, required = true),
        PermissionItem(BLUETOOTH_SCAN, BLUETOOTH_SCAN in granted, required = false),
        PermissionItem(POST_NOTIFICATIONS, POST_NOTIFICATIONS in granted, required = false),
        PermissionItem(OVERLAY, canDrawOverlays, required = false),
    )

    fun canContinue(items: List<PermissionItem>): Boolean = items.filter { it.required }.all { it.granted }
}
