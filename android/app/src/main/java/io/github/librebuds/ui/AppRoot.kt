// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.librebuds.BuildConfig
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.R
import io.github.librebuds.beacon.BeaconScanner
import io.github.librebuds.companion.AssociationStore
import io.github.librebuds.popup.PopupPresenter
import io.github.librebuds.popup.artFor
import io.github.librebuds.popup.demoPopupModel
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.state.DEMO_ADDRESS
import io.github.librebuds.ui.screens.DeviceScreen
import io.github.librebuds.ui.screens.HomeScreen
import io.github.librebuds.ui.screens.MultipointScreen
import io.github.librebuds.ui.screens.SettingsScreen
import io.github.librebuds.ui.screens.onboarding.OnboardingScreen
import io.github.librebuds.ui.screens.onboarding.openAppDetailsSettings
import io.github.librebuds.ui.screens.onboarding.rememberOverlayAccess
import io.github.librebuds.ui.theme.DesignSystem
import io.github.librebuds.ui.theme.LibreBudsTheme

/**
 * Top-level navigation over a [BackStack]: onboarding once, then the list of known pairs as the root,
 * with a pair's device screen, settings and multipoint on top. Back pops one screen and leaves the
 * app from the list. The app starts on the list; only when opened for a pair from the notification
 * or island ([launchAddress]) does it open that pair, with the list below it.
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
    launchAddress: String? = null,
    onShowEarbuds: (address: String) -> Unit = {},
    onLinkCompanion: (address: String, name: String, onResult: (linked: Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onExportDiagnostics: () -> Unit = {},
    onSaveDiagnostics: () -> Unit = {}
) {
    var designSystem by remember { mutableStateOf(preferences.designSystem) }
    var showOffMode by remember { mutableStateOf(preferences.showOffMode) }
    var showIsland by remember { mutableStateOf(preferences.showIsland) }
    var popupEnabled by remember { mutableStateOf(preferences.popupEnabled) }
    val context = LocalContext.current
    var demoMode by remember { mutableStateOf(preferences.demoMode) }
    var popupStyle by remember { mutableStateOf(preferences.popupStyle) }
    val detection = rememberDetection()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val registry = remember { LibreBudsApp.from(context).registry }
    val showDemo = BuildConfig.DEBUG && demoMode
    val profileOf = { id: String -> registry.profiles.firstOrNull { it.id == id } }
    val pairs = pairRows(detection, ui.state, showDemo, profileOf)
    val rowOf = { address: String -> pairs.firstOrNull { it.address.equals(address, ignoreCase = true) } }
    var stack by rememberSaveable(stateSaver = BackStackSaver) {
        // Opened for a pair (notification, island): straight into it, with the list below.
        val launched = launchAddress?.let(rowOf)?.let { Route.Device(it.address, it.name) }
        mutableStateOf(BackStack.initial(preferences.onboardingDone, launched))
    }
    // The pair of the open device screen, else the one the app would start on (Settings' companion row).
    val selected = stack.device?.let { rowOf(it.address) }
        ?: chooseStartPair(pairs, detection.lastConnectedAt, ui.state)?.let(rowOf)
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

    // The one-time start jump, once detection has seen the links (the first read may not have).
    LaunchedEffect(detection.settled, stack.startPending, stack.top) {
        if (!detection.settled || !stack.startPending) return@LaunchedEffect
        val start = startPair(pairs)
        stack = stack.openStart(start?.let { Route.Device(it.address, it.name) })
    }
    // A pair that is gone (unpaired, demo mode turned off) closes its screen when it is on top.
    val shownDevice = stack.top as? Route.Device
    val shownGone = shownDevice != null && detection.settled && rowOf(shownDevice.address) == null
    LaunchedEffect(shownGone) {
        if (shownGone) goBack()
    }
    // Disabled on the root (the list), so Back there falls through to the activity and leaves the app.
    BackHandler(enabled = stack.canGoBack, onBack = goBack)
    val homeTitle = stringResource(R.string.app_name)
    val titleOf: @Composable (Route) -> String = { route ->
        when (route) {
            Route.Home, Route.Onboarding -> homeTitle
            is Route.Device -> rowOf(route.address)?.label ?: route.name
            Route.Settings -> stringResource(R.string.settings)
            Route.Multipoint -> stringResource(R.string.section_multipoint)
        }
    }
    val backLabel: @Composable () -> String? = { stack.entries.getOrNull(stack.entries.lastIndex - 1)?.let { titleOf(it) } }

    LibreBudsTheme(m3eEnabled = designSystem == DesignSystem.Material) {
        screenStates.SaveableStateProvider(stack.topKey()) {
            when (val top = stack.top) {
                Route.Onboarding -> OnboardingScreen(preferences = preferences, onDone = { stack = stack.finishOnboarding() })
                Route.Home -> HomeScreen(
                    pairs = pairs,
                    permissionMissing = detection.permissionMissing,
                    onOpen = { pair -> stack = stack.push(Route.Device(pair.address, pair.name)) },
                    onOpenSettings = { stack = stack.push(Route.Settings) }
                )
                is Route.Device -> {
                    // Before detection read the pair again (or for the frame before a gone pair closes).
                    val pair = rowOf(top.address) ?: PairRow(top.address, top.name, model = null, connected = false, battery = null)
                    DeviceScreen(
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel,
                        showOffMode = showOffMode,
                        pair = pair,
                        backLabel = backLabel() ?: homeTitle,
                        onShown = { if (pair.address != DEMO_ADDRESS) onShowEarbuds(pair.address) },
                        onNavigateBack = goBack,
                        onOpenSettings = { stack = stack.push(Route.Settings) },
                        onOpenMultipoint = { stack = stack.push(Route.Multipoint) }
                    )
                }
                Route.Multipoint -> MultipointScreen(viewModel = settingsViewModel, onNavigateBack = goBack, backLabel = backLabel())
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
                        // Read fresh every time Settings is entered, since BeaconReceiver writes it outside Compose.
                        lastBeacon = preferences.lastBeacon,
                        onShowTestPopup = {
                            val app = LibreBudsApp.from(context)
                            demoPopupModel(app.registry)?.let { model ->
                                PopupPresenter.show(app, model, artFor(model.profileId, model.art))
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
                        onNavigateBack = goBack,
                        backLabel = backLabel()
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
