// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.qs

import android.os.Bundle
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.R
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.ui.DeviceViewModel
import io.github.librebuds.ui.components.BatteryView
import io.github.librebuds.ui.components.ControlCenterNoiseControlSegmentedButton
import io.github.librebuds.ui.messageRes
import io.github.librebuds.ui.model.NoiseControlMode
import io.github.librebuds.ui.model.toUiBatteries
import io.github.librebuds.ui.theme.DesignSystem
import io.github.librebuds.ui.theme.LibreBudsTheme

/** Compact control panel opened from the Quick Settings tile; tapping outside the card closes it. */
class QuickSettingsDialogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setGravity(Gravity.BOTTOM)
        val viewModel = ViewModelProvider(
            this,
            viewModelFactory { initializer { DeviceViewModel(LibreBudsApp.from(this@QuickSettingsDialogActivity).repository) } }
        )[DeviceViewModel::class.java]
        val preferences = AppPreferences(this)
        val modes = NoiseControlMode.entries.filter { it != NoiseControlMode.OFF || preferences.showOffMode }
        setContent {
            LibreBudsTheme(m3eEnabled = preferences.designSystem == DesignSystem.Material) {
                QuickSettingsPanel(viewModel, modes, onDismiss = ::finish)
            }
        }
    }
}

private val PanelColor = Color(0xF21C1C1E)

@Composable
private fun QuickSettingsPanel(viewModel: DeviceViewModel, modes: List<NoiseControlMode>, onDismiss: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val batteries = ui.state.battery.toUiBatteries()
    val context = LocalContext.current
    val art = remember(ui.state.profileId) {
        LibreBudsApp.from(context).registry.profiles.firstOrNull { it.id == ui.state.profileId }?.art ?: "generic"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .background(PanelColor, RoundedCornerShape(32.dp))
                // Consumes taps on the card so only the area around it dismisses.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = ui.state.name ?: stringResource(R.string.app_name),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            if (!ui.state.isConnected) {
                Text(
                    text = stringResource(R.string.not_connected),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (batteries.isNotEmpty()) {
                BatteryView(batteries, art)
            }
            if (ui.state.isConnected && "anc" in ui.state.capabilities) {
                ControlCenterNoiseControlSegmentedButton(
                    availableModes = modes,
                    selectedMode = ui.selectedNoiseMode,
                    onModeSelected = viewModel::selectNoiseMode
                )
            }
            ui.error?.let { error ->
                Text(text = stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
