// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.provider.Settings
import android.util.Log
import io.github.librebuds.BuildConfig
import io.github.librebuds.protocol.beacon.FdeeBeacon
import io.github.librebuds.state.AppPreferences

/**
 * Registers a system BLE scan for the case-open beacon (service data 0xFDEE). Results reach
 * [BeaconReceiver] through a PendingIntent, so no service has to run while the scan is active.
 *
 * Android throttles apps that start scans too often, so routine triggers (app start, boot, update)
 * go through [ensureStarted], which leaves a registration from this boot and app version alone.
 * [start] and [stop] are for explicit changes: the settings toggle, a new scan permission, Bluetooth on.
 */
object BeaconScanner {
    private const val TAG = "BeaconScanner"
    private const val ACTION_RESULTS = "io.github.librebuds.action.BEACON_RESULTS"

    /** Starts the scan when enabled and not already registered in this boot by this app version. */
    fun ensureStarted(context: Context): Boolean {
        val preferences = AppPreferences(context)
        val needed = scanStartNeeded(
            enabled = preferences.popupEnabled,
            registered = preferences.scanMarker,
            current = currentMarker(context),
            intentAlive = intentAlive(context),
        )
        return if (needed) start(context) else false
    }

    /** Starts (or restarts) the scan; false without the scan permission, an adapter or Bluetooth on. */
    fun start(context: Context): Boolean {
        val preferences = AppPreferences(context)
        preferences.scanMarker = null
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return false
        val adapter = adapter(context) ?: return false
        val scanner = adapter.bluetoothLeScanner ?: return false
        val filter = ScanFilter.Builder()
            .setServiceData(ParcelUuid.fromString(FdeeBeacon.SERVICE_UUID), byteArrayOf(), byteArrayOf())
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(scanCallbackType(offloadedFiltering = adapter.isOffloadedFilteringSupported))
            .build()
        val intent = pendingIntent(context)
        return try {
            // Registering the same PendingIntent twice is not documented as idempotent; start clean.
            scanner.stopScan(intent)
            val status = scanner.startScan(listOf(filter), settings, intent)
            if (status != 0) Log.w(TAG, "Beacon scan not started: $status")
            if (status == 0) preferences.scanMarker = currentMarker(context)
            status == 0
        } catch (e: SecurityException) {
            Log.w(TAG, "Beacon scan refused", e)
            false
        } catch (e: IllegalStateException) {
            // Bluetooth was turned off between the checks and the call.
            Log.w(TAG, "Beacon scan not started", e)
            false
        } catch (e: IllegalArgumentException) {
            // The stack refused the scan settings (for example the callback type).
            Log.w(TAG, "Beacon scan settings refused", e)
            false
        }
    }

    fun stop(context: Context) {
        AppPreferences(context).scanMarker = null
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

    /**
     * Follows the adapter while the process lives: turning Bluetooth off drops the scan, turning it
     * on registers it again. [BluetoothAdapter.ACTION_STATE_CHANGED] is not delivered to manifest
     * receivers, so this is a runtime registration.
     */
    fun watchBluetooth(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_OFF -> AppPreferences(context).scanMarker = null
                    BluetoothAdapter.STATE_ON -> if (AppPreferences(context).popupEnabled) start(context)
                }
            }
        }
        // Only the system sends this protected broadcast; exported just makes sure it is delivered.
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), Context.RECEIVER_EXPORTED)
    }

    private fun currentMarker(context: Context) = ScanMarker(
        bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1),
        versionCode = BuildConfig.VERSION_CODE,
    )

    /** Force stop and reboot cancel the app's PendingIntents together with the scan. */
    private fun intentAlive(context: Context): Boolean = PendingIntent.getBroadcast(
        context, 0, resultIntent(context), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE,
    ) != null

    private fun adapter(context: Context): BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    /** Null when there is no adapter or Bluetooth is off. */
    private fun scanner(context: Context): BluetoothLeScanner? = adapter(context)?.bluetoothLeScanner

    private fun resultIntent(context: Context) = Intent(context, BeaconReceiver::class.java).setAction(ACTION_RESULTS)

    // Mutable: the system fills in the scan results; the explicit target keeps it app-internal.
    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, resultIntent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}

/**
 * With hardware (offloaded) filtering, report a beacon once when it appears and once when it is lost
 * instead of every advertisement, which keeps the app from waking on each packet while a case sits
 * open. Without it, FIRST_MATCH/MATCH_LOST are not available and every match is delivered.
 */
fun scanCallbackType(offloadedFiltering: Boolean): Int =
    if (offloadedFiltering) ScanSettings.CALLBACK_TYPE_FIRST_MATCH or ScanSettings.CALLBACK_TYPE_MATCH_LOST
    else ScanSettings.CALLBACK_TYPE_ALL_MATCHES

/** A MATCH_LOST delivery says the beacon went away; it never raises a popup. */
fun isMatchLost(callbackType: Int): Boolean = callbackType == ScanSettings.CALLBACK_TYPE_MATCH_LOST
