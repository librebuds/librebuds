// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds

import android.Manifest
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import io.github.librebuds.companion.CompanionLink
import io.github.librebuds.diag.DiagnosticsExport
import io.github.librebuds.diag.DiagnosticsHeader
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var associationStore: AssociationStore

    // Registered before onCreate's content is set, as the contract API requires.
    private val saveDiagnosticsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let(::writeDiagnosticsTo)
    }

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
                onOpenEarbuds = ::openEarbuds,
                onExportDiagnostics = ::exportDiagnostics,
                onSaveDiagnostics = ::saveDiagnosticsToFile
            )
        }
    }

    /** A visible activity may start the foreground service, which covers missed presence events. */
    override fun onStart() {
        super.onStart()
        // Covers a scan permission granted in the system settings and a force stop, which dropped the scan.
        BeaconScanner.ensureStarted(this)
        val stored = associationStore.primary() ?: return
        startIfConnected(stored.address, stored.name)
    }

    /**
     * Starts the connection service for [address] once the earbuds are known to be connected. [AclTracker]
     * is empty after the process restarted, so the connected A2DP and headset devices are asked too, and
     * earbuds found there are recorded in [AclTracker].
     */
    private fun startIfConnected(address: String, name: String?) {
        if (AclTracker.isConnected(address)) {
            BudsService.start(this, address, name)
            return
        }
        var started = false
        refreshAudioConnections(this) {
            if (started || !AudioConnections.contains(address)) return@refreshAudioConnections
            AclTracker.onConnected(address)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                started = true
                BudsService.start(this, address, name)
            }
        }
    }

    /**
     * A device screen opened [address]. The first time, the system companion dialog links these earbuds
     * to the app (for background presence) and they become the stored pair. They connect if they are
     * connected to the phone, also when the dialog was declined; it returns on the next visit. The controller holds one pair, so the last opened earbuds are the ones it follows.
     */
    private fun openEarbuds(address: String, name: String, onResult: (Boolean) -> Unit) {
        // Connect first: a dismissed dialog may never call back, and the visible app needs no association.
        startIfConnected(address, name)
        if (associationStore.primary()?.address.equals(address, ignoreCase = true)) {
            onResult(true)
            return
        }
        CompanionLink(this).associate(this, address, name) { stored -> onResult(stored != null) }
    }

    /**
     * Shares a header, the recent app events and the frame log as a cache file; a large log would not
     * fit in an intent extra. The header makes the file useful even when no frame was exchanged.
     */
    private fun exportDiagnostics() {
        val app = LibreBudsApp.from(this)
        val header = diagnosticsHeader(app)
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                // Redacting a full frame log takes a while; keep it off the main thread.
                val text = diagnosticsText(app, header)
                val dir = File(cacheDir, DIAGNOSTICS_DIR).apply { mkdirs() }
                val file = File(dir, "librebuds-diagnostics.jsonl").apply { writeText(text) }
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

    /**
     * Opens the system document picker for a new file named with the current time, so the same
     * redacted export [exportDiagnostics] shares can also be kept as a file the person chose.
     */
    private fun saveDiagnosticsToFile() {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val create = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(DIAGNOSTICS_MIME_TYPE)
            .putExtra(Intent.EXTRA_TITLE, "librebuds-diagnostics-$stamp.jsonl")
        try {
            saveDiagnosticsLauncher.launch(create)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No document picker available", e)
        }
    }

    /** Writes the same redacted export [exportDiagnostics] shares into the file [uri] now points at. */
    private fun writeDiagnosticsTo(uri: Uri) {
        val app = LibreBudsApp.from(this)
        val header = diagnosticsHeader(app)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val text = diagnosticsText(app, header)
                // "wt" truncates an existing file; plain "w" appends on some document providers.
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            }
        }
    }

    private fun diagnosticsText(app: LibreBudsApp, header: DiagnosticsHeader): String =
        DiagnosticsExport.build(header, app.eventLog, app.frameLog)

    private fun diagnosticsHeader(app: LibreBudsApp): DiagnosticsHeader {
        fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val state = app.controller.state.value
        val bluetoothEnabled = try {
            getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
        } catch (e: SecurityException) {
            null
        }
        val associations = try {
            getSystemService(CompanionDeviceManager::class.java)?.myAssociations?.size
        } catch (e: RuntimeException) {
            null
        }
        return DiagnosticsHeader(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            commit = BuildConfig.BUILD_COMMIT,
            sdkInt = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            bluetoothEnabled = bluetoothEnabled,
            connectGranted = granted(Manifest.permission.BLUETOOTH_CONNECT),
            scanGranted = granted(Manifest.permission.BLUETOOTH_SCAN),
            notificationsGranted = granted(Manifest.permission.POST_NOTIFICATIONS),
            overlayGranted = Settings.canDrawOverlays(this),
            associations = associations,
            link = state.link.name,
            lastError = state.lastError?.name,
            profileId = state.profileId,
            frames = app.frameLog.size(),
            exportedAt = System.currentTimeMillis(),
        )
    }

    private companion object {
        const val TAG = "MainActivity"
        const val DIAGNOSTICS_DIR = "diagnostics"
        // Not a registered IANA type, but exactly that keeps providers like DocumentsUI from guessing a
        // different extension (e.g. appending .txt to a text/plain name) for the suggested .jsonl name.
        const val DIAGNOSTICS_MIME_TYPE = "application/json-lines"
    }
}
