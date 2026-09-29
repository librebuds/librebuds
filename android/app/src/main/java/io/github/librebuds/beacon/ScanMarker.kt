// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

/**
 * Identifies one beacon scan registration: the boot it was made in and the app version that made it.
 * A reboot or an update drops PendingIntent scans, so a marker from another boot or version is stale.
 */
data class ScanMarker(val bootCount: Int, val versionCode: Int) {
    fun encode(): String = "$bootCount:$versionCode"

    companion object {
        fun decode(text: String?): ScanMarker? {
            val parts = text?.split(':')?.takeIf { it.size == 2 } ?: return null
            val boot = parts[0].toIntOrNull() ?: return null
            val version = parts[1].toIntOrNull() ?: return null
            return ScanMarker(boot, version)
        }
    }
}

/**
 * Whether the scan must be (re)registered: Android throttles apps that start scans too often, so an
 * unchanged registration is left alone. [intentAlive] is false once the result PendingIntent is gone
 * (force stop), which also drops the scan.
 */
fun scanStartNeeded(enabled: Boolean, registered: ScanMarker?, current: ScanMarker, intentAlive: Boolean): Boolean =
    enabled && (registered != current || !intentAlive)
