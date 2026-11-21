// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.librebuds.BuildConfig
import io.github.librebuds.R
import io.github.librebuds.popup.PopupStyle
import io.github.librebuds.ui.components.ListItemOrientation
import io.github.librebuds.ui.components.StyledList
import io.github.librebuds.ui.components.StyledListItem
import io.github.librebuds.ui.components.StyledScaffold
import io.github.librebuds.ui.components.StyledToggle
import io.github.librebuds.ui.screens.onboarding.OverlayAccess
import io.github.librebuds.ui.screens.onboarding.PermissionHintRow
import io.github.librebuds.ui.theme.DesignSystem
import io.github.librebuds.ui.theme.AccentPreset

@Composable
fun SettingsScreen(
    designSystem: DesignSystem,
    onDesignSystemChange: (DesignSystem) -> Unit,
    amoledBlack: Boolean,
    onAmoledBlackChange: (Boolean) -> Unit,
    accentPreset: AccentPreset,
    onAccentPresetChange: (AccentPreset) -> Unit,
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
    companionPair: String?,
    companionLinked: Boolean,
    onLinkCompanion: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onSaveDiagnostics: () -> Unit,
    onNavigateBack: () -> Unit,
    /** The previous screen's title, shown on the Apple-style back button. */
    backLabel: String? = null
) {
    StyledScaffold(
        title = stringResource(R.string.settings),
        showBackButton = true,
        onNavigateBack = onNavigateBack,
        backLabel = backLabel
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(screenContentPadding())
        ) {
            StyledList(title = "Appearance") {
                StyledToggle(
                    label = stringResource(R.string.material_you_style),
                    checked = designSystem == DesignSystem.Material,
                    onCheckedChange = { onDesignSystemChange(if (it) DesignSystem.Material else DesignSystem.Apple) }
                )
                StyledToggle(
                    label = stringResource(R.string.amoled_black),
                    description = stringResource(R.string.amoled_black_description),
                    checked = amoledBlack,
                    onCheckedChange = onAmoledBlackChange
                )
            }
            Text(
                text = "Accent color",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccentPreset.entries.forEach { preset ->
                    val selected = preset == accentPreset
                    Column(
                        modifier = Modifier.clickable { onAccentPresetChange(preset) }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(preset.dark)
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Text("✓", color = Color.Black)
                        }
                        Text(preset.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }
            StyledList(title = "App controls") {
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
