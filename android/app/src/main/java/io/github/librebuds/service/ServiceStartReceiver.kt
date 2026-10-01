// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts the connection service after a reboot and after an app update, so paired FreeBuds connect
 * without opening the app first. Both broadcasts allow a foreground service start from the background.
 */
class ServiceStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        BudsService.start(context)
    }
}
