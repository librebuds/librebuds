// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.librebuds.R
import io.github.librebuds.companion.BondedDevice
import io.github.librebuds.companion.CompanionLink
import io.github.librebuds.companion.Stored
import io.github.librebuds.companion.candidates
import io.github.librebuds.profile.ProfileAssets
import io.github.librebuds.ui.components.MaterialButtonStyle
import io.github.librebuds.ui.components.StyledButton
import io.github.librebuds.ui.components.StyledList
import io.github.librebuds.ui.components.StyledListItem
import io.github.librebuds.ui.components.StyledScaffold

/** Paired devices, or null when the Bluetooth permission is missing. */
private fun bondedDevices(context: Context): List<BondedDevice>? {
    if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return null
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return try {
        adapter.bondedDevices.orEmpty().map { BondedDevice(it.name, it.address) }
    } catch (_: SecurityException) {
        null
    }
}

/** Lists paired Bluetooth devices and associates the chosen one through the system companion dialog. */
@Composable
fun AddDeviceScreen(
    onAssociated: (Stored) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val registry = remember { ProfileAssets.load(context) }
    val link = remember { CompanionLink(context) }
    var bonded by remember { mutableStateOf(bondedDevices(context)) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    // Re-read on return from the Bluetooth settings, where the user may just have paired the earbuds.
    // Also unlocks the list after the system dialog closed, in case it was dismissed without a callback.
    LifecycleResumeEffect(Unit) {
        bonded = bondedDevices(context)
        busy = false
        onPauseOrDispose { }
    }

    StyledScaffold(
        title = stringResource(R.string.add_earbuds),
        showBackButton = true,
        onNavigateBack = onNavigateBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(screenContentPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val devices = bonded
            val list = devices?.let { candidates(it, registry) }.orEmpty()
            if (list.isEmpty()) {
                Text(
                    text = stringResource(if (devices == null) R.string.bluetooth_permission_missing else R.string.pair_first),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                StyledButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    backdrop = rememberLayerBackdrop(),
                    materialButtonStyle = MaterialButtonStyle.Filled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.open_bluetooth_settings), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                StyledList {
                    list.forEach { candidate ->
                        StyledListItem(
                            name = candidate.name,
                            description = stringResource(
                                if (candidate.known) R.string.supported_model else R.string.unknown_model_basic
                            ),
                            enabled = !busy,
                            onClick = {
                                failed = false
                                if (activity == null) {
                                    failed = true
                                    return@StyledListItem
                                }
                                busy = true
                                link.associate(activity, candidate.address) { stored ->
                                    busy = false
                                    if (stored != null) {
                                        onAssociated(stored)
                                        onNavigateBack()
                                    } else {
                                        failed = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
            if (failed) {
                Text(
                    text = stringResource(R.string.association_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
