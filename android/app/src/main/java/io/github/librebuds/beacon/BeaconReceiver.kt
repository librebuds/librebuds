// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import android.util.Log
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.companion.AssociationStore
import io.github.librebuds.popup.PopupPresenter
import io.github.librebuds.popup.PopupVideos
import io.github.librebuds.popup.artFor
import io.github.librebuds.popup.popupModel
import io.github.librebuds.protocol.beacon.FdeeBeacon
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.state.AppPreferences

/**
 * Receives the beacon scan results from [BeaconScanner] and raises the popup when [PopupRules] says so.
 * Runs on the main thread (manifest receiver); it never connects to the earbuds.
 */
class BeaconReceiver : BroadcastReceiver() {
    private val rules = PopupRules()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra(BluetoothLeScanner.EXTRA_ERROR_CODE)) {
            Log.w(TAG, "Beacon scan failed: ${intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)}")
            return
        }
        val results = intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
            ?: return
        val app = LibreBudsApp.from(context)
        val preferences = AppPreferences(context)
        val associated = associatedProfile(context, app.registry)
        val rawResults = results.map { RawSighting(it.device.address, it.rssi, it.scanRecord?.getServiceData(FDEE)) }
        // Wall clock, like the stored cooldown; the results' own timestamps are elapsed realtime.
        val now = System.currentTimeMillis()
        // Every parsed beacon, not just ones that show a popup: unknown or far-away devices still update it.
        lastBeaconOf(rawResults, now)?.let { preferences.lastBeacon = it }
        val verdicts = judgeBatch(
            results = rawResults,
            now = now,
            rules = rules,
            registry = app.registry,
            associated = associated,
            lastShownAt = preferences::lastPopupAt,
        )
        for (verdict in verdicts) {
            if (verdict.decision != PopupDecision.SHOW) continue
            val beacon = verdict.sighting.beacon
            val profile = popupProfile(beacon, app.registry, associated) ?: continue
            PopupPresenter.show(app, popupModel(beacon, profile), artFor(profile.art, preferences.artVariant, PopupVideos.map()))
            preferences.markPopupShown(cooldownKey(beacon), verdict.sighting.atMillis)
        }
    }

    /** The profile of the earbuds added in the app, found by their Bluetooth name; null if none or unknown. */
    private fun associatedProfile(context: Context, registry: ProfileRegistry): Profile? {
        val stored = AssociationStore(context).primary() ?: return null
        return registry.match(btName = stored.name).takeIf { it.id != ProfileRegistry.GENERIC.id }
    }

    private companion object {
        const val TAG = "BeaconReceiver"
        val FDEE: ParcelUuid = ParcelUuid.fromString(FdeeBeacon.SERVICE_UUID)
    }
}
