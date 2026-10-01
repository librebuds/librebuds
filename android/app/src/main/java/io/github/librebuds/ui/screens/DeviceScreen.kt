// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.librebuds.R
import io.github.librebuds.state.LinkState
import io.github.librebuds.ui.DeviceViewModel
import io.github.librebuds.ui.SettingsViewModel
import io.github.librebuds.ui.components.AboutCard
import io.github.librebuds.ui.components.BatteryView
import io.github.librebuds.ui.components.NoiseControlSettings
import io.github.librebuds.ui.components.StyledButton
import io.github.librebuds.ui.components.StyledIconButton
import io.github.librebuds.ui.components.StyledScaffold
import io.github.librebuds.ui.messageRes
import io.github.librebuds.ui.model.toUiBatteries
import io.github.librebuds.ui.theme.DesignSystem
import io.github.librebuds.ui.theme.LocalDesignSystem

/** Padding that keeps screen content clear of the Apple-style floating header and the system bars. */
@Composable
internal fun screenContentPadding(): PaddingValues {
    val apple = LocalDesignSystem.current == DesignSystem.Apple
    val top = if (apple) WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp else 8.dp
    val bottom = (if (apple) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp) + 12.dp
    return PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = bottom)
}

/**
 * One pair of earbuds. [onOpened] runs once per visit to link these earbuds to the app (the system
 * companion dialog the first time) and reports whether they are associated. The controller holds one
 * pair at a time: while it is busy with other earbuds, this screen only shows that these are not connected.
 */
@Composable
fun DeviceScreen(
    viewModel: DeviceViewModel,
    settingsViewModel: SettingsViewModel,
    showOffMode: Boolean,
    address: String,
    name: String,
    art: String,
    onOpened: (onResult: (associated: Boolean) -> Unit) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMultipoint: () -> Unit
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val settings by settingsViewModel.ui.collectAsStateWithLifecycle()
    val batteries = ui.state.battery.toUiBatteries()
    var requested by rememberSaveable(address) { mutableStateOf(false) }
    var declined by rememberSaveable(address) { mutableStateOf(false) }
    LaunchedEffect(address) {
        if (requested) return@LaunchedEffect
        requested = true
        onOpened { associated -> declined = !associated }
    }
    val current = address.equals(ui.state.address, ignoreCase = true)

    StyledScaffold(
        title = name,
        showBackButton = true,
        onNavigateBack = onNavigateBack,
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
            if (declined) {
                Text(
                    text = stringResource(R.string.association_declined),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            if (!current) {
                LinkBanner(takenOver = false, updatedAtMillis = null, showHint = true, onTakeOver = {})
                return@Column
            }
            if (!ui.state.isConnected) {
                LinkBanner(
                    takenOver = ui.state.link == LinkState.TAKEN_OVER,
                    updatedAtMillis = ui.state.updatedAtMillis,
                    // Only when nothing is known yet this session (no battery either); once there is a
                    // stale value to show, "Last updated" below is the useful line, not this hint.
                    showHint = batteries.isEmpty(),
                    onTakeOver = viewModel::takeOver
                )
            }
            if (batteries.isNotEmpty()) {
                BatteryView(batteries, art)
            }
            if (ui.state.isConnected && "anc" in ui.state.capabilities) {
                NoiseControlSettings(
                    selected = ui.selectedNoiseMode,
                    showOff = showOffMode,
                    onSelected = viewModel::selectNoiseMode
                )
            }
            ui.error?.let { error ->
                Text(
                    text = stringResource(error.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            // Like noise control, settings only show while connected: a change could not be sent otherwise.
            if (ui.state.isConnected) {
                DeviceSettingsSections(settings, onChange = settingsViewModel::apply, onOpenMultipoint = onOpenMultipoint)
                settings.error?.let { error ->
                    Text(
                        text = stringResource(error.messageRes()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            AboutCard(ui.state.device.model, ui.state.device.firmware, ui.state.device.serial)
        }
    }
}

/**
 * Shown while the earbuds are not connected; the last known values stay visible below it. [showHint]
 * adds a line explaining what to do, for when there is nothing else to show yet (no stale value, not
 * taken over by another device).
 */
@Composable
private fun LinkBanner(takenOver: Boolean, updatedAtMillis: Long?, showHint: Boolean, onTakeOver: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(if (takenOver) R.string.controlled_by_other else R.string.not_connected),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium
        )
        updatedAtMillis?.let { millis ->
            Text(
                text = stringResource(R.string.last_updated, formatUpdatedAt(context, millis)),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (showHint && !takenOver && updatedAtMillis == null) {
            Text(
                text = stringResource(R.string.open_case_near_phone),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (takenOver) {
            StyledButton(
                onClick = onTakeOver,
                backdrop = rememberLayerBackdrop(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(text = stringResource(R.string.take_over), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Localized short time, with the date added when it was not today. Shared with the home list. */
internal fun formatUpdatedAt(context: Context, millis: Long): String {
    val dateFlags = if (DateUtils.isToday(millis)) 0 else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
    return DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME or dateFlags)
}
