// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import android.content.Context
import androidx.core.content.edit
import io.github.librebuds.ui.theme.DesignSystem

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) = prefs.edit { putBoolean("onboarding_done", value) }

    var designSystem: DesignSystem
        get() = runCatching { DesignSystem.valueOf(prefs.getString("design_system", DesignSystem.Apple.name)!!) }
            .getOrDefault(DesignSystem.Apple)
        set(value) = prefs.edit { putString("design_system", value.name) }

    /** Whether a runtime permission request was ever launched; survives restarts. */
    fun wasRequested(permission: String): Boolean = prefs.getBoolean("requested_$permission", false)

    fun markRequested(permission: String) = prefs.edit { putBoolean("requested_$permission", true) }

    var showOffMode: Boolean
        get() = prefs.getBoolean("show_off_mode", true)
        set(value) = prefs.edit { putBoolean("show_off_mode", value) }
}
