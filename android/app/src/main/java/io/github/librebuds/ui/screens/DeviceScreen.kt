// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.librebuds.R
import io.github.librebuds.ui.DeviceViewModel
import io.github.librebuds.ui.components.AboutCard
import io.github.librebuds.ui.components.BatteryView
import io.github.librebuds.ui.components.NoiseControlSettings
import io.github.librebuds.ui.components.StyledIconButton
import io.github.librebuds.ui.components.StyledScaffold
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

@Composable
fun DeviceScreen(
    viewModel: DeviceViewModel,
    showOffMode: Boolean,
    onOpenSettings: () -> Unit
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val batteries = ui.state.battery.toUiBatteries()

    StyledScaffold(
        title = ui.state.name ?: stringResource(R.string.app_name),
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
            if (!ui.state.isConnected) {
                Text(
                    text = stringResource(R.string.not_connected),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            if (batteries.isNotEmpty()) {
                BatteryView(batteries)
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
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            AboutCard(ui.state.device.model, ui.state.device.firmware, ui.state.device.serial)
        }
    }
}
