// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.librebuds.R
import io.github.librebuds.protocol.command.HostAction
import io.github.librebuds.protocol.command.HostRow
import io.github.librebuds.state.SettingChange
import io.github.librebuds.ui.SettingsViewModel
import io.github.librebuds.ui.components.StyledBottomSheet
import io.github.librebuds.ui.components.StyledList
import io.github.librebuds.ui.components.StyledListItem
import io.github.librebuds.ui.components.StyledScaffold
import io.github.librebuds.ui.components.StyledToggle
import io.github.librebuds.ui.messageRes
import io.github.librebuds.ui.model.controlKey

/**
 * Multipoint: the "connect to two devices" toggle and the hosts the earbuds know. A host's sheet
 * offers "Set as preferred" and Connect/Disconnect only; unpairing is deliberately not offered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultipointScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var selectedMac by remember { mutableStateOf<String?>(null) }
    val experimental = stringResource(R.string.experimental).takeIf { "multipoint" in ui.model.experimental }
    // Without a session every action would fail: show why and offer none.
    val connected = ui.state.isConnected

    StyledScaffold(
        title = stringResource(R.string.section_multipoint),
        showBackButton = true,
        onNavigateBack = onNavigateBack
    ) {
        PullToRefreshBox(
            isRefreshing = ui.refreshingHosts,
            onRefresh = { if (connected) viewModel.refreshHosts() },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(screenContentPadding()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!connected) {
                    Text(
                        text = stringResource(R.string.multipoint_not_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                ui.model.multipointEnabled?.let { enabled ->
                    StyledList(description = experimental) {
                        StyledToggle(
                            label = stringResource(R.string.connect_two_devices),
                            checked = enabled,
                            enabled = connected,
                            onCheckedChange = { viewModel.apply(SettingChange.MultipointEnabled(it)) }
                        )
                    }
                }
                if (ui.state.hosts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_hosts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    StyledList(title = stringResource(R.string.paired_devices)) {
                        ui.state.hosts.forEach { host ->
                            StyledListItem(
                                name = host.name ?: host.mac,
                                description = stringResource(host.statusRes()),
                                onClick = if (connected) ({ selectedMac = host.mac }) else null,
                                leadingContent = { PreferredMark(host.preferred) }
                            )
                        }
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
            }
        }
    }

    val selected = ui.state.hosts.firstOrNull { it.mac == selectedMac }?.takeIf { connected }
    StyledBottomSheet(visible = selected != null, onDismiss = { selectedMac = null }, backdrop = rememberLayerBackdrop()) { _, _ ->
        val host = selected ?: return@StyledBottomSheet
        HostActions(
            host = host,
            busy = SettingChange.HostCommand(HostAction.CONNECT, host.mac).controlKey() in ui.pending,
            onChange = {
                viewModel.apply(it)
                selectedMac = null
            }
        )
    }
}

@Composable
private fun HostActions(host: HostRow, busy: Boolean, onChange: (SettingChange) -> Unit) {
    Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp)) {
        Text(
            text = host.name ?: host.mac,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
        )
        StyledList {
            if (!host.preferred) {
                StyledListItem(
                    name = stringResource(R.string.set_as_preferred),
                    onClick = { onChange(SettingChange.PreferredHost(host.mac)) }
                )
            }
            val action = if (host.connected) HostAction.DISCONNECT else HostAction.CONNECT
            StyledListItem(
                name = stringResource(if (host.connected) R.string.disconnect else R.string.connect),
                enabled = !busy,
                onClick = { onChange(SettingChange.HostCommand(action, host.mac)) }
            )
        }
        if (host.connected) {
            // The earbuds do not say which host is this phone; disconnecting it drops the app's link too.
            Text(
                text = stringResource(R.string.host_disconnect_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
        }
    }
}

/** A star for the preferred host; the same space stays empty for the others so names line up. */
@Composable
private fun PreferredMark(preferred: Boolean) {
    Box(modifier = Modifier.size(20.dp)) {
        if (preferred) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(R.string.preferred_device),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun HostRow.statusRes(): Int = when {
    playing -> R.string.host_playing
    connected -> R.string.host_connected
    else -> R.string.not_connected
}
