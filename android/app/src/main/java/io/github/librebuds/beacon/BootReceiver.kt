// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The system drops PendingIntent scans on reboot and app update; registers the beacon scan again.
 * The app start that delivers this broadcast usually did so already, and then this is a no-op.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        BeaconScanner.ensureStarted(context)
    }
}
