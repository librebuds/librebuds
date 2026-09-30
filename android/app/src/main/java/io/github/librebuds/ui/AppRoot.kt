// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.librebuds.BuildConfig
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.beacon.BeaconScanner
import io.github.librebuds.companion.AssociationStore
import io.github.librebuds.popup.PopupPresenter
import io.github.librebuds.popup.PopupVideos
import io.github.librebuds.popup.artFor
import io.github.librebuds.popup.demoPopupModel
import io.github.librebuds.companion.Stored
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.ui.screens.AddDeviceScreen
import io.github.librebuds.ui.screens.DeviceScreen
import io.github.librebuds.ui.screens.MultipointScreen
import io.github.librebuds.ui.screens.SettingsScreen
import io.github.librebuds.ui.screens.onboarding.OnboardingScreen
import io.github.librebuds.ui.screens.onboarding.openAppDetailsSettings
import io.github.librebuds.ui.screens.onboarding.rememberOverlayAccess
import io.github.librebuds.ui.theme.DesignSystem
import io.github.librebuds.ui.theme.LibreBudsTheme

private const val ONBOARDING = "onboarding"
private const val DEVICE = "device"
private const val SETTINGS = "settings"
private const val ADD_DEVICE = "add_device"
private const val MULTIPOINT = "multipoint"

/**
 * Top-level navigation: onboarding once, then the device screen with settings, multipoint and the earbud picker on top.
 * [onAssociated] runs after the user associated new earbuds (the Bluetooth service hooks in here);
 * [onExportDiagnostics] shares the diagnostics file (header, recent events, frame log).
 */
@Composable
fun AppRoot(
    viewModel: DeviceViewModel,
    settingsViewModel: SettingsViewModel,
    preferences: AppPreferences,
    associationStore: AssociationStore,
    onAssociated: (Stored) -> Unit = {},
    onExportDiagnostics: () -> Unit = {}
) {
    var screen by rememberSaveable { mutableStateOf(if (preferences.onboardingDone) DEVICE else ONBOARDING) }
    var designSystem by remember { mutableStateOf(preferences.designSystem) }
    var showOffMode by remember { mutableStateOf(preferences.showOffMode) }
    var showIsland by remember { mutableStateOf(preferences.showIsland) }
    var popupEnabled by remember { mutableStateOf(preferences.popupEnabled) }
    val context = LocalContext.current
    var demoMode by remember { mutableStateOf(preferences.demoMode) }
    var artVariant by remember { mutableStateOf(preferences.artVariant) }
    var stored by remember { mutableStateOf(associationStore.primary()) }

    LibreBudsTheme(m3eEnabled = designSystem == DesignSystem.Material) {
        when (screen) {
            ONBOARDING -> OnboardingScreen(preferences = preferences, onDone = { screen = DEVICE })
            DEVICE -> DeviceScreen(
                viewModel = viewModel,
                settingsViewModel = settingsViewModel,
                showOffMode = showOffMode,
                hasDevice = stored != null || (BuildConfig.DEBUG && demoMode),
                onAddDevice = { screen = ADD_DEVICE },
                onOpenSettings = { screen = SETTINGS },
                onOpenMultipoint = { screen = MULTIPOINT }
            )
            MULTIPOINT -> {
                BackHandler { screen = DEVICE }
                MultipointScreen(viewModel = settingsViewModel, onNavigateBack = { screen = DEVICE })
            }
            ADD_DEVICE -> {
                BackHandler { screen = DEVICE }
                AddDeviceScreen(
                    onAssociated = {
                        stored = it
                        onAssociated(it)
                    },
                    onNavigateBack = { screen = DEVICE }
                )
            }
            SETTINGS -> {
                BackHandler { screen = DEVICE }
                val overlay = rememberOverlayAccess(preferences)
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
                    showIsland = showIsland,
                    onShowIslandChange = {
                        showIsland = it
                        preferences.showIsland = it
                    },
                    popupEnabled = popupEnabled,
                    onPopupEnabledChange = {
                        popupEnabled = it
                        preferences.popupEnabled = it
                        if (it) {
                            BeaconScanner.start(context)
                        } else {
                            BeaconScanner.stop(context)
                            PopupPresenter.dismiss(context)
                        }
                    },
                    overlay = overlay,
                    onOpenAppInfo = { openAppDetailsSettings(context) },
                    demoMode = demoMode,
                    onDemoModeChange = {
                        demoMode = it
                        preferences.demoMode = it
                    },
                    artVariant = artVariant,
                    onArtVariantChange = {
                        artVariant = it
                        preferences.artVariant = it
                    },
                    // Read fresh every time Settings is entered, since BeaconReceiver writes it outside Compose.
                    lastBeacon = preferences.lastBeacon,
                    onShowTestPopup = {
                        val app = LibreBudsApp.from(context)
                        demoPopupModel(app.registry)?.let { model ->
                            PopupPresenter.show(app, model, artFor(model.art, preferences.artVariant, PopupVideos.map()))
                        }
                    },
                    onExportDiagnostics = onExportDiagnostics,
                    onNavigateBack = { screen = DEVICE }
                )
            }
        }
    }
}
