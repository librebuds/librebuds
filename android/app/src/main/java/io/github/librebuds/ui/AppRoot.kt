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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.librebuds.BuildConfig
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.beacon.BeaconScanner
import io.github.librebuds.companion.disambiguatedLabels
import io.github.librebuds.popup.PopupPresenter
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.popup.PopupVideos
import io.github.librebuds.popup.artFor
import io.github.librebuds.popup.demoPopupModel
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.DEMO_ADDRESS
import io.github.librebuds.state.DEMO_NAME
import io.github.librebuds.state.batterySummaryNoBreak
import io.github.librebuds.ui.screens.DeviceScreen
import io.github.librebuds.ui.screens.HomeRow
import io.github.librebuds.ui.screens.HomeScreen
import io.github.librebuds.ui.screens.MultipointScreen
import io.github.librebuds.ui.screens.SettingsScreen
import io.github.librebuds.ui.screens.onboarding.OnboardingScreen
import io.github.librebuds.ui.screens.onboarding.openAppDetailsSettings
import io.github.librebuds.ui.screens.onboarding.rememberOverlayAccess
import io.github.librebuds.ui.theme.DesignSystem
import io.github.librebuds.ui.theme.LibreBudsTheme

/**
 * Top-level navigation over a [BackStack]: onboarding once, then home (the detected earbuds) as the
 * root, with a device screen per pair and settings and multipoint on top. Back pops one screen and
 * leaves the app from home. [onOpenEarbuds] links the earbuds a device screen shows to the app and
 * starts their connection (the Bluetooth side lives in the activity); [onExportDiagnostics] shares
 * the diagnostics file (header, recent events, frame log) and [onSaveDiagnostics] writes the same
 * export to a file the person picks.
 */
@Composable
fun AppRoot(
    viewModel: DeviceViewModel,
    settingsViewModel: SettingsViewModel,
    preferences: AppPreferences,
    onOpenEarbuds: (address: String, name: String, onResult: (associated: Boolean) -> Unit) -> Unit = { _, _, _ -> },
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
    val rows = homeRows(detection, ui.state, showDemo, profileOf)
    // Keeps each open screen's saved state (scroll position, the device screen's one association
    // request) while a screen above it shows; dropped once the screen is popped.
    val screenStates = rememberSaveableStateHolder()
    val goBack: () -> Unit = {
        stack.back()?.let { previous ->
            screenStates.removeState(stack.topKey())
            stack = previous
        }
    }

    // The demo row never counts: it would hide home on every start while demo mode is on.
    LaunchedEffect(detection.settled, stack.top) {
        if (!detection.settled) return@LaunchedEffect
        stack = stack.autoOpen(detection.buds.filter { detection.isConnected(it.address) }.map { Route.Device(it.address, it.name) })
    }
    // Disabled on the root, so Back there falls through to the activity and leaves the app.
    BackHandler(enabled = stack.canGoBack, onBack = goBack)

    LibreBudsTheme(m3eEnabled = designSystem == DesignSystem.Material) {
        screenStates.SaveableStateProvider(stack.topKey()) {
            when (val top = stack.top) {
                Route.Onboarding -> OnboardingScreen(preferences = preferences, onDone = { stack = stack.finishOnboarding() })
                Route.Home -> HomeScreen(
                    rows = rows,
                    permissionMissing = detection.permissionMissing,
                    onOpenDevice = { row -> stack = stack.push(Route.Device(row.address, row.name)) },
                    onOpenSettings = { stack = stack.push(Route.Settings) }
                )
                is Route.Device -> DeviceScreen(
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel,
                    showOffMode = showOffMode,
                    address = top.address,
                    name = top.name,
                    art = deviceArt(top.address, detection, ui.state, profileOf),
                    onOpened = { onResult ->
                        if (top.address == DEMO_ADDRESS) onResult(true) else onOpenEarbuds(top.address, top.name, onResult)
                    },
                    onNavigateBack = goBack,
                    onOpenSettings = { stack = stack.push(Route.Settings) },
                    onOpenMultipoint = { stack = stack.push(Route.Multipoint) }
                )
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

/**
 * The profile `art` shape for the device screen at [address]: from the detected pair's profile, else
 * (the demo row, or a pair no longer detected) from the controller's current profile.
 */
private fun deviceArt(address: String, detection: Detection, state: BudsState, profileOf: (String) -> Profile?): String {
    val profileId = detection.buds.firstOrNull { it.address.equals(address, ignoreCase = true) }?.profileId ?: state.profileId
    return profileOf(profileId)?.art ?: "generic"
}

/**
 * The home list: the detected earbuds (battery from the controller when it holds that pair), plus
 * the demo earbuds first when [showDemo]. [profileOf] maps a profile id to its profile. Two
 * detected pairs with the same name (two physical units of the same model) get the row label, not
 * the routing name, suffixed with their address so they are tellable apart; the demo row never collides.
 * Internal so [homeRows] itself can be unit tested without going through Compose.
 */
internal fun homeRows(detection: Detection, state: BudsState, showDemo: Boolean, profileOf: (String) -> Profile?): List<HomeRow> {
    fun row(address: String, name: String, model: String?, audioUp: Boolean, label: String = name, art: String = "generic"): HomeRow {
        val current = address.equals(state.address, ignoreCase = true)
        val connected = audioUp || (current && state.isConnected)
        return HomeRow(
            address = address,
            name = name,
            model = model,
            connected = connected,
            // Only while actually connected is the battery still live; once the link drops it is the
            // last known reading, not something to show as if current (see lastSeenMillis for that).
            battery = state.battery?.takeIf { current && connected }?.let(::batterySummaryNoBreak),
            lastSeenMillis = state.updatedAtMillis?.takeIf { current && !connected },
            label = label,
            art = art,
        )
    }
    val demoProfile = profileOf(state.profileId)
    val demo = if (showDemo) listOf(row(DEMO_ADDRESS, DEMO_NAME, demoProfile?.name, audioUp = false, art = demoProfile?.art ?: "generic")) else emptyList()
    val labels = disambiguatedLabels(detection.buds)
    return demo + detection.buds.map {
        row(it.address, it.name, it.model, detection.isConnected(it.address), labels.getValue(it.address), profileOf(it.profileId)?.art ?: "generic")
    }
}
