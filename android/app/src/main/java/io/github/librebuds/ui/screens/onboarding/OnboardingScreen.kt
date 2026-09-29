// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.librebuds.R
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.ui.components.MaterialButtonStyle
import io.github.librebuds.ui.components.StyledButton
import io.github.librebuds.ui.components.StyledScaffold
import io.github.librebuds.ui.screens.screenContentPadding

@Composable
fun OnboardingScreen(
    preferences: AppPreferences,
    onDone: () -> Unit
) {
    val permissions = rememberPermissionRequests(preferences)
    val canContinue = OnboardingState.canContinue(permissions.items)

    StyledScaffold(title = stringResource(R.string.welcome)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(screenContentPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            PermissionsPage(items = permissions.items, onRequest = permissions.request)
            StyledButton(
                onClick = {
                    preferences.onboardingDone = true
                    onDone()
                },
                backdrop = rememberLayerBackdrop(),
                enabled = canContinue,
                materialButtonStyle = MaterialButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.continue_action), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
