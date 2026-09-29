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
import io.github.librebuds.ui.components.StyledList
import io.github.librebuds.ui.components.StyledListItem
import io.github.librebuds.ui.components.StyledScaffold
import io.github.librebuds.ui.components.StyledToggle
import io.github.librebuds.ui.theme.DesignSystem

@Composable
fun SettingsScreen(
    designSystem: DesignSystem,
    onDesignSystemChange: (DesignSystem) -> Unit,
    showOffMode: Boolean,
    onShowOffModeChange: (Boolean) -> Unit,
    showIsland: Boolean,
    onShowIslandChange: (Boolean) -> Unit,
    demoMode: Boolean,
    onDemoModeChange: (Boolean) -> Unit,
    onExportDiagnostics: () -> Unit,
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
                if (BuildConfig.DEBUG) {
                    StyledToggle(
                        label = stringResource(R.string.demo_mode),
                        checked = demoMode,
                        onCheckedChange = onDemoModeChange
                    )
                }
                StyledListItem(
                    name = stringResource(R.string.export_diagnostics),
                    onClick = onExportDiagnostics
                )
                StyledListItem(
                    name = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                    description = stringResource(R.string.license_gpl)
                )
            }
        }
    }
}
