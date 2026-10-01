// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.bt

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.lang.reflect.Method

private const val TAG = "AclProbe"

/** `BluetoothDevice.isConnected()`: public to system apps only, looked up once; null when unavailable. */
private val isConnectedMethod: Method? by lazy {
    try {
        BluetoothDevice::class.java.getMethod("isConnected")
    } catch (e: Throwable) {
        Log.i(TAG, "BluetoothDevice.isConnected() not available: ${e.javaClass.simpleName}")
        null
    }
}

/**
 * Asks the system whether each of [addresses] has an ACL link right now and records the connected ones
 * in [AclTracker]. Covers earbuds that connected before this process started, when no ACL broadcast
 * arrived and no audio profile is up (an ACL-only link). Best effort: the method is not part of the
 * public SDK, so when the system blocks it nothing is recorded and the broadcasts and profile proxies
 * stay the only sources. It never marks an address as disconnected.
 */
fun probeAclConnections(context: Context, addresses: Collection<String>) {
    if (addresses.isEmpty()) return
    if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
    val method = isConnectedMethod ?: return
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
    for (address in addresses) {
        val connected = try {
            method.invoke(adapter.getRemoteDevice(address)) as? Boolean
        } catch (e: Throwable) {
            Log.i(TAG, "isConnected() failed: ${e.javaClass.simpleName}")
            return
        }
        if (connected == true) AclTracker.onConnected(address)
    }
}
