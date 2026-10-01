// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.librebuds.R
import io.github.librebuds.ui.components.MaterialButtonStyle
import io.github.librebuds.ui.components.StyledButton
import io.github.librebuds.ui.components.StyledIconButton
import io.github.librebuds.ui.components.StyledScaffold
import io.github.librebuds.ui.screens.onboarding.openAppDetailsSettings

/**
 * The root screen when there are no FreeBuds to show: none paired (with a way to Bluetooth settings),
 * or the Bluetooth permission is missing (with a way to App info). Settings stay reachable.
 */
@Composable
fun NoBudsScreen(permissionMissing: Boolean, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    StyledScaffold(
        title = stringResource(R.string.app_name),
        actionButtons = listOf { backdrop ->
            StyledIconButton(
                icon = Icons.Filled.Settings,
                backdrop = backdrop,
                onClick = onOpenSettings
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(screenContentPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (permissionMissing) {
                Hint(
                    text = stringResource(R.string.bluetooth_permission_missing),
                    action = stringResource(R.string.open_app_info),
                    onAction = { openAppDetailsSettings(context) }
                )
            } else {
                Hint(
                    text = stringResource(R.string.no_freebuds_paired),
                    action = stringResource(R.string.open_bluetooth_settings),
                    onAction = { openBluetoothSettings(context) }
                )
            }
        }
    }
}

/** Opens the system Bluetooth settings, where FreeBuds are paired; does nothing where there are none. */
internal fun openBluetoothSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // A device without Bluetooth settings cannot pair earbuds anyway.
    }
}

@Composable
private fun Hint(text: String, action: String, onAction: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    StyledButton(
        onClick = onAction,
        backdrop = rememberLayerBackdrop(),
        materialButtonStyle = MaterialButtonStyle.Filled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = action, style = MaterialTheme.typography.labelLarge)
    }
}
