// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens.onboarding

data class PermissionItem(val key: String, val granted: Boolean, val required: Boolean)

/** Extra guidance under a permission row once asking for it did not work. */
enum class PermissionHint {
    NONE,

    /** The overlay grant is still missing after the user was sent to its system page; restricted settings may block it. */
    RESTRICTED_SETTINGS,

    /** Android no longer shows the runtime permission dialog; only App info can grant it now. */
    APP_INFO,
}

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

    /**
     * True when Android will no longer show the permission dialog, so only the app's settings can grant it.
     * Before the first request shouldShowRationale is false too, hence [wasRequested].
     */
    fun shouldOpenSettings(granted: Boolean, wasRequested: Boolean, shouldShowRationale: Boolean): Boolean =
        !granted && wasRequested && !shouldShowRationale

    /**
     * Which hint to show under the row for [key]. The overlay has no dialog: once the user was sent to
     * its system page ([wasRequested]) and it is still not granted, Android may have blocked it as a
     * restricted setting (apps installed from a file). A runtime permission gets a hint when
     * [shouldOpenSettings] holds, which also covers a request denied without any dialog on the first try.
     */
    fun hintFor(key: String, granted: Boolean, wasRequested: Boolean, shouldShowRationale: Boolean = false): PermissionHint = when {
        granted -> PermissionHint.NONE
        key == OVERLAY -> if (wasRequested) PermissionHint.RESTRICTED_SETTINGS else PermissionHint.NONE
        shouldOpenSettings(granted = false, wasRequested = wasRequested, shouldShowRationale = shouldShowRationale) -> PermissionHint.APP_INFO
        else -> PermissionHint.NONE
    }

    fun canContinue(items: List<PermissionItem>): Boolean = items.filter { it.required }.all { it.granted }
}
