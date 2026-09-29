// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import android.content.Context
import androidx.core.content.edit
import io.github.librebuds.beacon.PopupTimes
import io.github.librebuds.beacon.ScanMarker
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

    /** Replaces the earbuds with demo data; read at app start and only honoured in debug builds. */
    var demoMode: Boolean
        get() = prefs.getBoolean("demo_mode", false)
        set(value) = prefs.edit { putBoolean("demo_mode", value) }

    var showIsland: Boolean
        get() = prefs.getBoolean("show_island", true)
        set(value) = prefs.edit { putBoolean("show_island", value) }

    /** Whether a case-open beacon may raise the popup; the beacon scan runs only while this is on. */
    var popupEnabled: Boolean
        get() = prefs.getBoolean("popup_enabled", true)
        set(value) = prefs.edit { putBoolean("popup_enabled", value) }

    /** Wall-clock time the popup was last shown for a cooldown [key] (see `cooldownKey`). */
    fun lastPopupAt(key: String): Long? = PopupTimes.decode(prefs.getString("popup_times", null))[key]

    /** Records a shown popup; the stored map keeps only the last hour, so it stays small. */
    fun markPopupShown(key: String, at: Long) = prefs.edit {
        putString("popup_times", PopupTimes.encode(PopupTimes.record(PopupTimes.decode(prefs.getString("popup_times", null)), key, at)))
    }

    /** The beacon scan registration currently believed active; null when none (or stopped). */
    var scanMarker: ScanMarker?
        get() = ScanMarker.decode(prefs.getString("scan_marker", null))
        set(value) = prefs.edit { if (value == null) remove("scan_marker") else putString("scan_marker", value.encode()) }
}
