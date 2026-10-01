// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.librebuds.BuildConfig
import io.github.librebuds.R
import io.github.librebuds.beacon.LastBeacon
import io.github.librebuds.popup.ArtVariant
import io.github.librebuds.popup.PopupStyle
import io.github.librebuds.ui.components.ListItemOrientation
import io.github.librebuds.ui.components.StyledList
import io.github.librebuds.ui.components.StyledListItem
import io.github.librebuds.ui.components.StyledScaffold
import io.github.librebuds.ui.components.StyledToggle
import io.github.librebuds.ui.screens.onboarding.OverlayAccess
import io.github.librebuds.ui.screens.onboarding.PermissionHintRow
import io.github.librebuds.ui.theme.DesignSystem
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    designSystem: DesignSystem,
    onDesignSystemChange: (DesignSystem) -> Unit,
    showOffMode: Boolean,
    onShowOffModeChange: (Boolean) -> Unit,
    showIsland: Boolean,
    onShowIslandChange: (Boolean) -> Unit,
    popupEnabled: Boolean,
    onPopupEnabledChange: (Boolean) -> Unit,
    popupStyle: PopupStyle,
    onPopupStyleChange: (PopupStyle) -> Unit,
    overlay: OverlayAccess,
    onOpenAppInfo: () -> Unit,
    demoMode: Boolean,
    onDemoModeChange: (Boolean) -> Unit,
    artVariant: ArtVariant,
    onArtVariantChange: (ArtVariant) -> Unit,
    lastBeacon: LastBeacon?,
    onShowTestPopup: () -> Unit,
    companionPair: String?,
    companionLinked: Boolean,
    onLinkCompanion: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onSaveDiagnostics: () -> Unit,
    onNavigateBack: () -> Unit
) {
    StyledScaffold(
        title = stringResource(R.string.settings),
        showBackButton = true,
        onNavigateBack = onNavigateBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(screenContentPadding())
        ) {
            StyledList {
                StyledToggle(
                    label = stringResource(R.string.material_you_style),
                    checked = designSystem == DesignSystem.Material,
                    onCheckedChange = { onDesignSystemChange(if (it) DesignSystem.Material else DesignSystem.Apple) }
                )
                StyledToggle(
                    label = stringResource(R.string.show_off_mode),
                    checked = showOffMode,
                    onCheckedChange = onShowOffModeChange
                )
                StyledToggle(
                    label = stringResource(R.string.show_island),
                    checked = showIsland,
                    onCheckedChange = onShowIslandChange
                )
                StyledToggle(
                    label = stringResource(R.string.popup_enabled),
                    checked = popupEnabled,
                    onCheckedChange = onPopupEnabledChange
                )
                // Two styles only, so a tap switches to the other one; the row shows the current choice.
                StyledListItem(
                    name = stringResource(R.string.popup_style),
                    description = stringResource(
                        if (popupStyle == PopupStyle.ISLAND) R.string.popup_style_island else R.string.popup_style_card
                    ),
                    enabled = popupEnabled,
                    onClick = { onPopupStyleChange(if (popupStyle == PopupStyle.ISLAND) PopupStyle.CARD else PopupStyle.ISLAND) }
                )
                // Optional: the service runs without it, but a companion association lets Android start it
                // from the background in cases a plain Bluetooth broadcast does not.
                companionPair?.let { pair ->
                    StyledListItem(
                        name = stringResource(R.string.background_link),
                        description = if (companionLinked) {
                            stringResource(R.string.background_linked)
                        } else {
                            stringResource(R.string.background_link_description, pair)
                        },
                        orientation = ListItemOrientation.Vertical,
                        onClick = if (companionLinked) null else onLinkCompanion
                    )
                }
                // Onboarding may have been skipped or blocked; the grant stays reachable here.
                if (!overlay.granted) {
                    StyledListItem(
                        name = stringResource(R.string.permission_overlay),
                        description = stringResource(R.string.overlay_missing_reason),
                        orientation = ListItemOrientation.Vertical,
                        onClick = overlay.request
                    )
                    PermissionHintRow(overlay.hint, onOpenAppInfo)
                }
                if (BuildConfig.DEBUG) {
                    StyledToggle(
                        label = stringResource(R.string.demo_mode),
                        checked = demoMode,
                        onCheckedChange = onDemoModeChange
                    )
                    StyledToggle(
                        label = stringResource(R.string.popup_art_variant),
                        checked = artVariant == ArtVariant.VIDEO,
                        // The artwork belongs to the card; the island has none.
                        enabled = popupStyle == PopupStyle.CARD,
                        onCheckedChange = { onArtVariantChange(if (it) ArtVariant.VIDEO else ArtVariant.VECTOR) }
                    )
                    StyledListItem(
                        name = stringResource(R.string.show_test_popup),
                        onClick = onShowTestPopup
                    )
                    StyledListItem(
                        name = stringResource(R.string.last_beacon),
                        // Vertical: the description is 3 lines (fields, time, hex) and needs the row's full
                        // width, not the narrow column Horizontal reserves to the right of the name.
                        orientation = ListItemOrientation.Vertical,
                        description = lastBeacon?.let {
                            val model = it.modelId ?: "?"
                            val sub = it.subModelId?.toString() ?: "?"
                            val reference = it.referenceRssi?.toString() ?: "?"
                            val time = DateFormat.getDateTimeInstance().format(Date(it.atMillis))
                            "model $model (sub $sub), RSSI ${it.rssi} dBm (ref $reference)\n$time\n${it.serviceDataHex}"
                        } ?: stringResource(R.string.last_beacon_empty)
                    )
                }
                // Share hands the redacted export to another app; save keeps it as a file the person picks.
                StyledListItem(
                    name = stringResource(R.string.share_diagnostics),
                    onClick = onExportDiagnostics
                )
                StyledListItem(
                    name = stringResource(R.string.save_diagnostics_to_file),
                    onClick = onSaveDiagnostics
                )
                StyledListItem(
                    name = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                    description = stringResource(R.string.license_gpl)
                )
            }
        }
    }
}
