// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.librebuds.beacon.BeaconScanner
import io.github.librebuds.bt.AclTracker
import io.github.librebuds.bt.AudioConnections
import io.github.librebuds.bt.refreshAudioConnections
import io.github.librebuds.companion.AssociationStore
import io.github.librebuds.companion.Stored
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.service.BudsService
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.ui.AppRoot
import io.github.librebuds.ui.DeviceViewModel
import io.github.librebuds.ui.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var associationStore: AssociationStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(
            this,
            viewModelFactory { initializer { DeviceViewModel(LibreBudsApp.from(this@MainActivity).repository) } }
        )[DeviceViewModel::class.java]
        val settingsViewModel = ViewModelProvider(
            this,
            viewModelFactory {
                initializer {
                    val app = LibreBudsApp.from(this@MainActivity)
                    SettingsViewModel(app.repository) { id -> app.registry.profiles.firstOrNull { it.id == id } ?: ProfileRegistry.GENERIC }
                }
            }
        )[SettingsViewModel::class.java]
        val preferences = AppPreferences(this)
        associationStore = AssociationStore(this)
        setContent {
            AppRoot(
                viewModel,
                settingsViewModel,
                preferences,
                associationStore,
                onAssociated = ::onAssociated,
                onExportDiagnostics = ::exportDiagnostics
            )
        }
    }

    /** A visible activity may start the foreground service, which covers missed presence events. */
    override fun onStart() {
        super.onStart()
        // Covers a scan permission granted in the system settings and a force stop, which dropped the scan.
        BeaconScanner.ensureStarted(this)
        val stored = associationStore.primary() ?: return
        if (AclTracker.isConnected(stored.address)) {
            BudsService.start(this, stored.address, stored.name)
        } else {
            startIfAudioConnected(stored)
        }
    }

    /**
     * [AclTracker] is empty after the process restarted, so also look for the stored earbuds among
     * the connected A2DP and headset devices, and record them in [AclTracker] when found.
     */
    private fun startIfAudioConnected(stored: Stored) {
        var started = false
        refreshAudioConnections(this) {
            if (started || !AudioConnections.contains(stored.address)) return@refreshAudioConnections
            AclTracker.onConnected(stored.address)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                started = true
                BudsService.start(this, stored.address, stored.name)
            }
        }
    }

    /** Starts the service for fresh earbuds and records them as present if their audio is already up. */
    private fun onAssociated(stored: Stored) {
        BudsService.start(this, stored.address, stored.name)
        refreshAudioConnections(this) {
            if (AudioConnections.contains(stored.address)) AclTracker.onConnected(stored.address)
        }
    }

    /** Shares the frame log as a cache file; a large log would not fit in an intent extra. */
    private fun exportDiagnostics() {
        val jsonl = LibreBudsApp.from(this).frameLog.toJsonl()
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                val dir = File(cacheDir, DIAGNOSTICS_DIR).apply { mkdirs() }
                val file = File(dir, "librebuds-diagnostics.jsonl").apply { writeText(jsonl) }
                FileProvider.getUriForFile(this@MainActivity, "$packageName.diagnostics", file)
            }
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.diagnostics_subject))
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(null, uri)
            try {
                startActivity(Intent.createChooser(send, getString(R.string.export_diagnostics)))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No app to share diagnostics with", e)
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
        const val DIAGNOSTICS_DIR = "diagnostics"
    }
}
