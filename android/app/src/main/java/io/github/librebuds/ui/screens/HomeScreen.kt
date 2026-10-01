// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

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
import io.github.librebuds.ui.components.ListItemOrientation
import io.github.librebuds.ui.components.MaterialButtonStyle
import io.github.librebuds.ui.components.StyledButton
import io.github.librebuds.ui.components.StyledIconButton
import io.github.librebuds.ui.components.StyledList
import io.github.librebuds.ui.components.StyledListItem
import io.github.librebuds.ui.components.StyledScaffold
import io.github.librebuds.ui.screens.onboarding.openAppDetailsSettings

/**
 * One line of the home list: the earbuds and what is known about them right now. [label] is what the
 * row shows (it may add the address to [name] when another row shares the same name); [name] is the
 * plain Bluetooth name, used for routing and association so it stays the one the rest of the app knows.
 */
data class HomeRow(
    val address: String,
    val name: String,
    val model: String?,
    val connected: Boolean,
    val battery: String?,
    val label: String = name
)

/**
 * The root screen: the paired FreeBuds the app detected, with the settings gear. Without the
 * Bluetooth permission or without paired FreeBuds it explains what to do instead.
 */
@Composable
fun HomeScreen(
    rows: List<HomeRow>,
    permissionMissing: Boolean,
    onOpenDevice: (HomeRow) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    StyledScaffold(
        title = stringResource(R.string.home_title),
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
            if (rows.isNotEmpty()) {
                StyledList {
                    rows.forEach { row ->
                        StyledListItem(
                            name = row.label,
                            description = rowDescription(row),
                            orientation = ListItemOrientation.Vertical,
                            onClick = { onOpenDevice(row) }
                        )
                    }
                }
            }
            if (permissionMissing) {
                Hint(
                    text = stringResource(R.string.bluetooth_permission_missing),
                    action = stringResource(R.string.open_app_info),
                    onAction = { openAppDetailsSettings(context) }
                )
            } else if (rows.isEmpty()) {
                Hint(
                    text = stringResource(R.string.no_freebuds_paired),
                    action = stringResource(R.string.open_bluetooth_settings),
                    onAction = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                )
            }
        }
    }
}

@Composable
private fun rowDescription(row: HomeRow): String = listOfNotNull(
    row.model ?: stringResource(R.string.unknown_model),
    stringResource(if (row.connected) R.string.buds_connected else R.string.not_connected),
    row.battery,
).joinToString(" · ")

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
