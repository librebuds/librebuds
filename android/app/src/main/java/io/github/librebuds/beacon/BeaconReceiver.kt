// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.bt.BtWorker
import io.github.librebuds.companion.AssociationStore
import io.github.librebuds.popup.PopupArt
import io.github.librebuds.popup.PopupModel
import io.github.librebuds.popup.PopupPresenter
import io.github.librebuds.popup.artFor
import io.github.librebuds.popup.popupModel
import io.github.librebuds.protocol.beacon.FdeeBeacon
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.state.AppPreferences

/**
 * Receives the beacon scan results from [BeaconScanner] and raises the popup when [PopupRules] says so.
 * The judging (preference and store reads, the paired devices' names) runs on [BtWorker], so a case
 * opening never blocks the main thread; only the popup itself is shown there. It never connects to the earbuds.
 */
class BeaconReceiver : BroadcastReceiver() {
    private val rules = PopupRules()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra(BluetoothLeScanner.EXTRA_ERROR_CODE)) {
            Log.w(TAG, "Beacon scan failed: ${intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)}")
            LibreBudsApp.from(context).eventLog.record(TAG, "scan failed: error ${intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)}")
            return
        }
        // With hardware filtering the scan also reports when the beacon goes away; that is not a case opening.
        if (isMatchLost(intent.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, ScanSettings.CALLBACK_TYPE_ALL_MATCHES))) return
        val results = intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
            ?: return
        val pending = goAsync()
        val app = LibreBudsApp.from(context)
        BtWorker.execute {
            val shows = try {
                judge(app, results)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Beacon batch failed", e)
                emptyList()
            }
            BtWorker.main {
                try {
                    for ((model, art) in shows) PopupPresenter.show(app, model, art)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    /**
     * Background: updates the beacon stores and returns the popups to show. The cooldown is recorded
     * here, before the popup is up, so the next batch (judged after this one on the same thread)
     * already sees it.
     */
    private fun judge(app: LibreBudsApp, results: List<ScanResult>): List<Pair<PopupModel, PopupArt>> {
        val context: Context = app
        val preferences = AppPreferences(context)
        val associated = associatedProfile(context, app.registry)
        val rawResults = results.map { RawSighting(it.device.address, it.rssi, it.scanRecord?.getServiceData(FDEE)) }
        // Wall clock, like the stored cooldown; the results' own timestamps are elapsed realtime.
        val now = System.currentTimeMillis()
        // Every parsed beacon, not just ones that show a popup: unknown or far-away devices still update it.
        lastBeaconOf(rawResults, now)?.let { if (shouldStoreLastBeacon(preferences.lastBeacon, it)) preferences.lastBeacon = it }
        val openingStore = CaseOpeningStore(context)
        val openings = openingStore.load().toMutableMap()
        val before = openings.toMap()
        val verdicts = judgeBatch(
            results = rawResults,
            now = now,
            rules = rules,
            registry = app.registry,
            associated = associated,
            lastShownAt = preferences::lastPopupAt,
            openings = openings,
            bonded = bondedProfiles(context, app.registry),
        )
        val pruned = CaseOpenings.prune(openings, now)
        if (CaseOpenings.worthSaving(before, pruned)) openingStore.save(pruned)
        for (line in decisionLogLines(verdicts, lastLogged)) Log.i(TAG, line)
        val shows = mutableListOf<Pair<PopupModel, PopupArt>>()
        for (verdict in verdicts) {
            if (verdict.decision != PopupDecision.SHOW) continue
            val profile = verdict.profile ?: continue
            shows += popupModel(profile, verdict.batteries) to artFor(profile.id, profile.art)
            preferences.markPopupShown(cooldownKey(verdict.sighting.beacon), verdict.sighting.atMillis)
        }
        return shows
    }

    /**
     * Profile ids of the earbuds bonded to this phone, matched by their Bluetooth name; null without
     * the connect permission or an adapter, when the bonded devices cannot be read.
     */
    private fun bondedProfiles(context: Context, registry: ProfileRegistry): Set<String>? {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return null
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        return try {
            adapter.bondedDevices.orEmpty()
                .mapNotNull { device -> device.name?.let { registry.match(btName = it) } }
                .filter { it.id != ProfileRegistry.GENERIC.id }
                .map { it.id }
                .toSet()
        } catch (e: SecurityException) {
            null
        }
    }

    /** The profile of the earbuds added in the app, found by their Bluetooth name; null if none or unknown. */
    private fun associatedProfile(context: Context, registry: ProfileRegistry): Profile? {
        val stored = AssociationStore(context).primary() ?: return null
        return registry.match(btName = stored.name).takeIf { it.id != ProfileRegistry.GENERIC.id }
    }

    private companion object {
        const val TAG = "BeaconReceiver"

        /** Last decision logged per cooldown key, for this process (see [decisionLogLines]). */
        val lastLogged = mutableMapOf<String, PopupDecision>()
        val FDEE: ParcelUuid = ParcelUuid.fromString(FdeeBeacon.SERVICE_UUID)
    }
}
