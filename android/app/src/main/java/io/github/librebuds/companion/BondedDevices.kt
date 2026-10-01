// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.companion

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

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
