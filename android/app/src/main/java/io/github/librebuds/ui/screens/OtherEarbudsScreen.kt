// LibreBuds - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.librebuds.R
import io.github.librebuds.ui.PairRow
import io.github.librebuds.ui.components.PremiumSegmentedTabs
import io.github.librebuds.ui.components.StyledButton
import io.github.librebuds.ui.components.StyledScaffold

/**
 * The existing QuickBuds-inspired component system, with an Android-level audio
 * status backend. Vendor commands remain unavailable until their protocol driver
 * is actually integrated and verified; avoid presenting fake ANC/EQ controls.
 */
@Composable
fun OtherEarbudsScreen(pair: PairRow, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var page by rememberSaveable(pair.address) { mutableIntStateOf(0) }
    StyledScaffold(
        title = pair.label,
        showBackButton = true,
        onNavigateBack = onNavigateBack,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(screenContentPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PremiumSegmentedTabs(
                labels = listOf(
                    stringResource(R.string.device_tab_home),
                    stringResource(R.string.device_tab_controls),
                    stringResource(R.string.device_tab_details)
                ),
                selected = page,
                onSelect = { page = it }
            )
            when (page) {
                0 -> {
                    DetailCard(
                        title = stringResource(R.string.device_battery_status),
                        description = if (pair.connected) {
                            pair.battery?.let { "${pair.name}: $it" }
                                ?: stringResource(R.string.other_battery_unknown)
                        } else stringResource(R.string.not_connected)
                    )
                    DetailCard(
                        title = stringResource(R.string.other_connected_audio),
                        description = stringResource(if (pair.connected) R.string.buds_connected else R.string.not_connected)
                    )
                    DetailCard(
                        title = stringResource(R.string.other_controls_note_title),
                        description = stringResource(R.string.other_controls_note)
                    )
                }
                1 -> {
                    DetailCard(
                        title = stringResource(R.string.other_controls_note_title),
                        description = stringResource(R.string.other_controls_note)
                    )
                    StyledButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        backdrop = rememberLayerBackdrop(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.open_bluetooth_settings))
                    }
                }
                else -> {
                    DetailCard(
                        title = stringResource(R.string.model_name),
                        description = pair.model ?: pair.name
                    )
                    DetailCard(
                        title = stringResource(R.string.other_brand),
                        description = pair.brand ?: stringResource(R.string.other_generic)
                    )
                    DetailCard(
                        title = stringResource(R.string.other_support_source),
                        description = stringResource(R.string.other_pairfree_credit)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailCard(title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
