// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.librebuds.bt.AclTracker
import io.github.librebuds.companion.AssociationStore
import io.github.librebuds.service.BudsService
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.ui.AppRoot
import io.github.librebuds.ui.DeviceViewModel

class MainActivity : ComponentActivity() {
    private lateinit var associationStore: AssociationStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(
            this,
            viewModelFactory { initializer { DeviceViewModel(LibreBudsApp.from(this@MainActivity).repository) } }
        )[DeviceViewModel::class.java]
        val preferences = AppPreferences(this)
        associationStore = AssociationStore(this)
        setContent {
            AppRoot(
                viewModel,
                preferences,
                associationStore,
                onAssociated = { BudsService.start(this, it.address, it.name) },
                onExportDiagnostics = ::exportDiagnostics
            )
        }
    }

    /** A visible activity may start the foreground service, which covers missed presence events. */
    override fun onStart() {
        super.onStart()
        val stored = associationStore.primary() ?: return
        if (AclTracker.isConnected(stored.address) || audioDeviceConnected()) {
            BudsService.start(this, stored.address, stored.name)
        }
    }

    /**
     * Whether some headset is connected. [AclTracker] is empty after the process restarted, so this
     * catches earbuds that connected earlier; if another device is connected the attempt fails and
     * the service stops again.
     */
    private fun audioDeviceConnected(): Boolean {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return false
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        return try {
            adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED ||
                adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED
        } catch (_: SecurityException) {
            false
        }
    }

    private fun exportDiagnostics() {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.diagnostics_subject))
            .putExtra(Intent.EXTRA_TEXT, LibreBudsApp.from(this).frameLog.toJsonl())
        startActivity(Intent.createChooser(send, getString(R.string.export_diagnostics)))
    }
}
