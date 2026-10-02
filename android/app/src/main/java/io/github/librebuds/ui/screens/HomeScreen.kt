// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.librebuds.R
import io.github.librebuds.ui.PairRow
import io.github.librebuds.ui.components.ListItemOrientation
import io.github.librebuds.ui.components.MaterialButtonStyle
import io.github.librebuds.ui.components.ProductArt
import io.github.librebuds.ui.components.StyledButton
import io.github.librebuds.ui.components.StyledIconButton
import io.github.librebuds.ui.components.StyledList
import io.github.librebuds.ui.components.StyledListItem
import io.github.librebuds.ui.components.StyledScaffold
import io.github.librebuds.ui.pairDescriptionText
import io.github.librebuds.ui.screens.onboarding.openAppDetailsSettings

/** How faded a greyed-out row's thumbnail and text are, like a disabled row. */
const val GREYED_ROW_ALPHA = 0.4f

/**
 * The root screen: the known FreeBuds in [pairs] order (see pairRows), the connected ones on top in
 * full colour and the others greyed out but still tappable; [onOpen] opens a pair's screen. Below
 * the list a row opens Bluetooth settings to pair another. Without the Bluetooth permission, or with
 * no FreeBuds paired, it says what to do instead. Settings stay reachable through the gear.
 */
@Composable
fun HomeScreen(pairs: List<PairRow>, permissionMissing: Boolean, onOpen: (PairRow) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    StyledScaffold(
        title = stringResource(R.string.app_name),
        actionButtons = listOf { backdrop ->
            StyledIconButton(
                icon = Icons.Filled.Settings,
                backdrop = backdrop,
                onClick = onOpenSettings
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(screenContentPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                permissionMissing -> Hint(
                    text = stringResource(R.string.bluetooth_permission_missing),
                    action = stringResource(R.string.open_app_info),
                    onAction = { openAppDetailsSettings(context) }
                )
                pairs.isEmpty() -> Hint(
                    text = stringResource(R.string.no_freebuds_paired),
                    action = stringResource(R.string.open_bluetooth_settings),
                    onAction = { openBluetoothSettings(context) }
                )
                else -> {
                    StyledList {
                        pairs.forEach { pair ->
                            StyledListItem(
                                name = pair.label,
                                description = pairDescriptionText(pair.model ?: stringResource(R.string.unknown_model), pair.battery),
                                orientation = ListItemOrientation.Vertical,
                                contentAlpha = if (pair.greyed) GREYED_ROW_ALPHA else 1f,
                                leadingContent = { ProductThumbnail(pair.art, pair.profileId) },
                                onClick = { onOpen(pair) }
                            )
                        }
                    }
                    StyledList {
                        StyledListItem(
                            name = stringResource(R.string.pair_new_earbuds),
                            onClick = { openBluetoothSettings(context) }
                        )
                    }
                }
            }
        }
    }
}

/** Opens the system Bluetooth settings, where FreeBuds are paired; does nothing where there are none. */
internal fun openBluetoothSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // A device without Bluetooth settings cannot pair earbuds anyway.
    }
}

/**
 * The earbuds render on a rounded tile: darker neutral grey in the light theme, dark grey in the
 * dark theme, with a hairline outline, so a white product render stays clearly visible against the
 * near-white card behind it in light theme too.
 */
@Composable
private fun ProductThumbnail(art: String, profileId: String? = null) {
    val dark = isSystemInDarkTheme()
    val tile = if (dark) Color(0xFF2C2C2E) else Color(0xFFD1D1D8)
    val outline = if (dark) Color(0x33FFFFFF) else Color(0x4D000000)
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(tile, shape)
            .border(1.dp, outline, shape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(ProductArt.thumbnail(art, profileId)),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun Hint(text: String, action: String, onAction: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    StyledButton(
        onClick = onAction,
        backdrop = rememberLayerBackdrop(),
        materialButtonStyle = MaterialButtonStyle.Filled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = action, style = MaterialTheme.typography.labelLarge)
    }
}
