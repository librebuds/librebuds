// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.companion

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import io.github.librebuds.LibreBudsApp

private const val TAG = "BondedDevices"

/**
 * The paired devices with their advertised service UUIDs, or null when the Bluetooth connect
 * permission is missing. Empty without an adapter or with Bluetooth off.
 */
fun readBondedDevices(context: Context): List<BondedDevice>? {
    if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return null
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return try {
        adapter.bondedDevices.orEmpty().map { device ->
            BondedDevice(device.name, device.address, device.uuids.orEmpty().map { it.toString() })
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "Cannot list paired devices", e)
        null
    }
}

/**
 * The paired FreeBuds (see [detectFreeBuds]), or null when the Bluetooth connect permission is missing.
 * Shared by the connection service and the screens so both recognise the same earbuds.
 */
fun readDetectedBuds(context: Context): List<DetectedBuds>? {
    val bonded = readBondedDevices(context) ?: return null
    return detectFreeBuds(bonded, LibreBudsApp.from(context).registry, AssociationStore(context).known())
}

/** Whether Bluetooth is on; false without an adapter or when the system refuses to tell. */
fun isBluetoothOn(context: Context): Boolean = try {
    context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
} catch (e: SecurityException) {
    false
}
