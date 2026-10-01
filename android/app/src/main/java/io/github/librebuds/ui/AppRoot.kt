// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.librebuds.BuildConfig
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.beacon.BeaconScanner
import io.github.librebuds.companion.AssociationStore
import io.github.librebuds.popup.PopupPresenter
import io.github.librebuds.popup.PopupVideos
import io.github.librebuds.popup.artFor
import io.github.librebuds.popup.demoPopupModel
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.state.DEMO_ADDRESS
import io.github.librebuds.ui.screens.DeviceScreen
import io.github.librebuds.ui.screens.MultipointScreen
import io.github.librebuds.ui.screens.NoBudsScreen
import io.github.librebuds.ui.screens.SettingsScreen
import io.github.librebuds.ui.screens.onboarding.OnboardingScreen
import io.github.librebuds.ui.screens.onboarding.openAppDetailsSettings
import io.github.librebuds.ui.screens.onboarding.rememberOverlayAccess
import io.github.librebuds.ui.theme.DesignSystem
import io.github.librebuds.ui.theme.LibreBudsTheme

/**
 * Top-level navigation over a [BackStack]: onboarding once, then the root, which is the device screen
 * of the chosen pair ([chooseStartPair]) or, with no FreeBuds paired, what to do about it. Settings
 * and multipoint open on top; Back pops one screen and leaves the app from the root. Which pair the
 * root shows follows the automatic choice until the user picks one in the switcher.
 * [onShowEarbuds] tells the activity which pair is on screen (the service may switch to it);
 * [onLinkCompanion] runs the optional companion association; [onExportDiagnostics] shares the
 * diagnostics file (header, recent events, frame log) and [onSaveDiagnostics] writes the same export
 * to a file the person picks.
 */
@Composable
fun AppRoot(
    viewModel: DeviceViewModel,
    settingsViewModel: SettingsViewModel,
    preferences: AppPreferences,
    onShowEarbuds: (address: String) -> Unit = {},
    onLinkCompanion: (address: String, name: String, onResult: (linked: Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onExportDiagnostics: () -> Unit = {},
    onSaveDiagnostics: () -> Unit = {}
) {
    var stack by rememberSaveable(stateSaver = BackStackSaver) { mutableStateOf(BackStack.initial(preferences.onboardingDone)) }
    var designSystem by remember { mutableStateOf(preferences.designSystem) }
    var showOffMode by remember { mutableStateOf(preferences.showOffMode) }
    var showIsland by remember { mutableStateOf(preferences.showIsland) }
    var popupEnabled by remember { mutableStateOf(preferences.popupEnabled) }
    val context = LocalContext.current
    var demoMode by remember { mutableStateOf(preferences.demoMode) }
    var popupStyle by remember { mutableStateOf(preferences.popupStyle) }
    var artVariant by remember { mutableStateOf(preferences.artVariant) }
    val detection = rememberDetection()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val registry = remember { LibreBudsApp.from(context).registry }
    val showDemo = BuildConfig.DEBUG && demoMode
    val profileOf = { id: String -> registry.profiles.firstOrNull { it.id == id } }
    // The pair the user picked in the switcher this session; until then the automatic choice applies.
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val unmarked = pairRows(detection, ui.state, showDemo, profileOf)
    val selectedAddress = picked?.takeIf { address -> unmarked.any { it.address.equals(address, ignoreCase = true) } }
        ?: chooseStartPair(unmarked, detection.lastConnectedAt, ui.state)
    val pairs = unmarked.map { it.copy(selected = it.address.equals(selectedAddress, ignoreCase = true)) }
    val selected = pairs.firstOrNull { it.selected }
    var companionLinked by remember { mutableStateOf(AssociationStore(context).known()) }
    // Keeps each open screen's saved state (scroll position) while a screen above it shows; dropped
    // once the screen is popped.
    val screenStates = rememberSaveableStateHolder()
    val goBack: () -> Unit = {
        stack.back()?.let { previous ->
            screenStates.removeState(stack.topKey())
            stack = previous
        }
    }

    // Disabled on the root, so Back there falls through to the activity and leaves the app.
    BackHandler(enabled = stack.canGoBack, onBack = goBack)

    LibreBudsTheme(m3eEnabled = designSystem == DesignSystem.Material) {
        screenStates.SaveableStateProvider(stack.topKey()) {
            when (stack.top) {
                Route.Onboarding -> OnboardingScreen(preferences = preferences, onDone = { stack = stack.finishOnboarding() })
                Route.Buds -> if (selected == null) {
                    NoBudsScreen(
                        permissionMissing = detection.permissionMissing,
                        onOpenSettings = { stack = stack.push(Route.Settings) }
                    )
                } else {
                    DeviceScreen(
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel,
                        showOffMode = showOffMode,
                        pair = selected,
                        pairs = pairs,
                        onShown = { if (selected.address != DEMO_ADDRESS) onShowEarbuds(selected.address) },
                        onPick = { picked = it.address },
                        onOpenSettings = { stack = stack.push(Route.Settings) },
                        onOpenMultipoint = { stack = stack.push(Route.Multipoint) }
                    )
                }
                Route.Multipoint -> MultipointScreen(viewModel = settingsViewModel, onNavigateBack = goBack)
                Route.Settings -> {
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
                        popupStyle = popupStyle,
                        onPopupStyleChange = {
                            popupStyle = it
                            preferences.popupStyle = it
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
                        companionPair = selected?.takeIf { it.address != DEMO_ADDRESS }?.label,
                        companionLinked = selected?.address?.uppercase() in companionLinked,
                        onLinkCompanion = {
                            selected?.let { pair ->
                                onLinkCompanion(pair.address, pair.name) { linked ->
                                    if (linked) companionLinked = AssociationStore(context).known()
                                }
                            }
                        },
                        onExportDiagnostics = onExportDiagnostics,
                        onSaveDiagnostics = onSaveDiagnostics,
                        onNavigateBack = goBack
                    )
                }
            }
        }
    }
}

private val BackStackSaver = Saver<BackStack, ArrayList<String>>(
    save = { it.encode() },
    restore = { BackStack.decode(it) }
)
