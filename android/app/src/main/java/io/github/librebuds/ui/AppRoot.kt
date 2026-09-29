// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.ui.screens.DeviceScreen
import io.github.librebuds.ui.screens.SettingsScreen
import io.github.librebuds.ui.screens.onboarding.OnboardingScreen
import io.github.librebuds.ui.theme.DesignSystem
import io.github.librebuds.ui.theme.LibreBudsTheme

private const val ONBOARDING = "onboarding"
private const val DEVICE = "device"
private const val SETTINGS = "settings"

/** Top-level navigation: onboarding once, then the device screen with settings on top. */
@Composable
fun AppRoot(viewModel: DeviceViewModel, preferences: AppPreferences) {
    var screen by rememberSaveable { mutableStateOf(if (preferences.onboardingDone) DEVICE else ONBOARDING) }
    var designSystem by remember { mutableStateOf(preferences.designSystem) }
    var showOffMode by remember { mutableStateOf(preferences.showOffMode) }

    LibreBudsTheme(m3eEnabled = designSystem == DesignSystem.Material) {
        when (screen) {
            ONBOARDING -> OnboardingScreen(preferences = preferences, onDone = { screen = DEVICE })
            DEVICE -> DeviceScreen(
                viewModel = viewModel,
                showOffMode = showOffMode,
                onOpenSettings = { screen = SETTINGS }
            )
            SETTINGS -> {
                BackHandler { screen = DEVICE }
                SettingsScreen(
                    designSystem = designSystem,
                    onDesignSystemChange = {
                        designSystem = it
                        preferences.designSystem = it
                    },
                    showOffMode = showOffMode,
                    onShowOffModeChange = {
                        showOffMode = it
                        preferences.showOffMode = it
                    },
                    onNavigateBack = { screen = DEVICE }
                )
            }
        }
    }
}
