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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.librebuds.R
import io.github.librebuds.state.LinkState
import io.github.librebuds.ui.DeviceViewModel
import io.github.librebuds.ui.PairRow
import io.github.librebuds.ui.SettingsViewModel
import io.github.librebuds.ui.components.AboutCard
import io.github.librebuds.ui.components.BatteryView
import io.github.librebuds.ui.components.NoiseControlSettings
import io.github.librebuds.ui.components.StyledButton
import io.github.librebuds.ui.components.StyledIconButton
import io.github.librebuds.ui.components.StyledScaffold
import io.github.librebuds.ui.messageRes
import io.github.librebuds.ui.model.offeredModes
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

/** What the device screen says about the link above the values, if anything. */
enum class LinkNotice { NONE, CONNECTING, PHONE_ONLY, NOT_CONNECTED, TAKEN_OVER }

/**
 * The notice for a pair: [current] is whether the controller holds this pair, [phoneConnected] its
 * real Bluetooth state toward the phone (ACL, A2DP or headset), [link] the controller's link. A pair
 * the phone has but the controller does not (yet) gets [LinkNotice.PHONE_ONLY], never "Not connected".
 */
fun linkNotice(current: Boolean, phoneConnected: Boolean, link: LinkState): LinkNotice {
    if (!current) return if (phoneConnected) LinkNotice.PHONE_ONLY else LinkNotice.NOT_CONNECTED
    return when (link) {
        LinkState.CONNECTED -> LinkNotice.NONE
        LinkState.CONNECTING -> LinkNotice.CONNECTING
        LinkState.TAKEN_OVER -> LinkNotice.TAKEN_OVER
        LinkState.DISCONNECTED -> if (phoneConnected) LinkNotice.PHONE_ONLY else LinkNotice.NOT_CONNECTED
    }
}

/**
 * One pair of earbuds, [pair], opened from the list. The back button (labelled [backLabel], the
 * list's title) and the system Back return to the list. [onShown] runs once per pair shown and again
 * when the phone connects it, so the service may switch the controller to it when it is connected to
 * the phone. The controller holds one pair at a time: for a pair it does not hold, this screen only
 * shows its connection state toward the phone.
 */
@Composable
fun DeviceScreen(
    viewModel: DeviceViewModel,
    settingsViewModel: SettingsViewModel,
    showOffMode: Boolean,
    pair: PairRow,
    backLabel: String,
    onShown: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMultipoint: () -> Unit
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val settings by settingsViewModel.ui.collectAsStateWithLifecycle()
    val batteries = ui.state.battery.toUiBatteries()
    val address = pair.address
    // Again once the phone has the pair: picked while still in its case, it then becomes the service's choice.
    LaunchedEffect(address, pair.connected) { onShown() }
    val current = address.equals(ui.state.address, ignoreCase = true)
    val notice = linkNotice(current, pair.connected, ui.state.link)

    StyledScaffold(
        title = pair.label,
        showBackButton = true,
        onNavigateBack = onNavigateBack,
        backLabel = backLabel,
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
            if (notice != LinkNotice.NONE) {
                LinkBanner(
                    notice = notice,
                    // Only the controller's own pair has last known values to date.
                    lastSeenMillis = ui.state.updatedAtMillis?.takeIf { current && batteries.isNotEmpty() },
                    onTakeOver = viewModel::takeOver
                )
            }
            if (!current) return@Column
            if (batteries.isNotEmpty()) {
                BatteryView(batteries, pair.art, pair.profileId)
            }
            if (ui.state.isConnected && "anc" in ui.state.capabilities) {
                NoiseControlSettings(
                    selected = ui.selectedNoiseMode,
                    modes = offeredModes(ui.listedModes, showOffMode),
                    onSelected = viewModel::selectNoiseMode
                )
                ui.cancellationLevel?.let { picker ->
                    CancellationLevelList(picker, onSelect = viewModel::selectCancellationLevel)
                }
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
                DeviceSettingsSections(settings, onChange = settingsViewModel::apply, onOpenMultipoint = onOpenMultipoint, onRing = settingsViewModel::ring)
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
 * Shown while the controller has no live link to this pair. [lastSeenMillis] marks the last known
 * values below it as such; the take-over button shows only while another device holds the earbuds.
 */
@Composable
private fun LinkBanner(notice: LinkNotice, lastSeenMillis: Long?, onTakeOver: () -> Unit) {
    val context = LocalContext.current
    val calm = notice == LinkNotice.CONNECTING || notice == LinkNotice.PHONE_ONLY
    val container = if (calm) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    val onContainer = if (calm) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(
                when (notice) {
                    LinkNotice.TAKEN_OVER -> R.string.controlled_by_other
                    LinkNotice.CONNECTING -> R.string.connecting
                    LinkNotice.PHONE_ONLY -> R.string.phone_connected_waiting
                    LinkNotice.NOT_CONNECTED, LinkNotice.NONE -> R.string.not_connected_take_out
                }
            ),
            color = onContainer,
            style = MaterialTheme.typography.bodyMedium
        )
        lastSeenMillis?.let { millis ->
            Text(
                text = stringResource(R.string.last_seen_line, formatUpdatedAt(context, millis)),
                color = onContainer,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (notice == LinkNotice.TAKEN_OVER) {
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

/** Localized short time, with the date added when it was not today. */
internal fun formatUpdatedAt(context: Context, millis: Long): String {
    val dateFlags = if (DateUtils.isToday(millis)) 0 else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
    return DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME or dateFlags)
}
