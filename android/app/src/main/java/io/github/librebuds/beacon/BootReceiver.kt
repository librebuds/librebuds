// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.librebuds.state.AppPreferences

/** The system drops PendingIntent scans on reboot and app update; registers the beacon scan again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (AppPreferences(context).popupEnabled) BeaconScanner.start(context)
    }
}
