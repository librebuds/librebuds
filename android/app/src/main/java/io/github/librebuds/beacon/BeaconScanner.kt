// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import io.github.librebuds.protocol.beacon.FdeeBeacon

/**
 * Registers a system BLE scan for the case-open beacon (service data 0xFDEE). Results reach
 * [BeaconReceiver] through a PendingIntent, so no service has to run while the scan is active.
 */
object BeaconScanner {
    private const val TAG = "BeaconScanner"
    private const val ACTION_RESULTS = "io.github.librebuds.action.BEACON_RESULTS"

    /** Starts (or restarts) the scan; false without the scan permission, an adapter or Bluetooth on. */
    fun start(context: Context): Boolean {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return false
        val scanner = scanner(context) ?: return false
        val filter = ScanFilter.Builder()
            .setServiceData(ParcelUuid.fromString(FdeeBeacon.SERVICE_UUID), byteArrayOf(), byteArrayOf())
            .build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        val intent = pendingIntent(context)
        return try {
            // Registering the same PendingIntent twice is not documented as idempotent; start clean.
            scanner.stopScan(intent)
            val status = scanner.startScan(listOf(filter), settings, intent)
            if (status != 0) Log.w(TAG, "Beacon scan not started: $status")
            status == 0
        } catch (e: SecurityException) {
            Log.w(TAG, "Beacon scan refused", e)
            false
        } catch (e: IllegalStateException) {
            // Bluetooth was turned off between the checks and the call.
            Log.w(TAG, "Beacon scan not started", e)
            false
        }
    }

    fun stop(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
        val scanner = scanner(context) ?: return
        try {
            scanner.stopScan(pendingIntent(context))
        } catch (e: SecurityException) {
            Log.w(TAG, "Beacon scan stop refused", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Beacon scan not stopped", e)
        }
    }

    /** Null when there is no adapter or Bluetooth is off. */
    private fun scanner(context: Context): BluetoothLeScanner? =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner

    // Mutable: the system fills in the scan results; the explicit target keeps it app-internal.
    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, BeaconReceiver::class.java).setAction(ACTION_RESULTS),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}
