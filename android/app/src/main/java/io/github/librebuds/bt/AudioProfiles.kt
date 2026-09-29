// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.bt

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

private const val TAG = "AudioProfiles"

/**
 * Asks the A2DP and headset profile proxies which devices are connected and records the answers in
 * [AudioConnections]. Never blocks: each proxy answers later on the main thread, which then runs
 * [onUpdated]. Does nothing without the Bluetooth permission.
 */
fun refreshAudioConnections(context: Context, onUpdated: () -> Unit = {}) {
    if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
    val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            try {
                AudioConnections.update(profile, proxy.connectedDevices.map { it.address })
                onUpdated()
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot list connected audio devices", e)
            } finally {
                adapter.closeProfileProxy(profile, proxy)
            }
        }

        override fun onServiceDisconnected(profile: Int) = Unit
    }
    adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
    adapter.getProfileProxy(context, listener, BluetoothProfile.HEADSET)
}
