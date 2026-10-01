// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

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
import io.github.librebuds.ui.components.ListItemOrientation
import io.github.librebuds.ui.components.MaterialButtonStyle
import io.github.librebuds.ui.components.ProductArt
import io.github.librebuds.ui.components.StyledButton
import io.github.librebuds.ui.components.StyledIconButton
import io.github.librebuds.ui.components.StyledList
import io.github.librebuds.ui.components.StyledListItem
import io.github.librebuds.ui.components.StyledScaffold
import io.github.librebuds.ui.screens.onboarding.openAppDetailsSettings

/**
 * One line of the home list: the earbuds and what is known about them right now. [label] is what the
 * row shows (it may add the address to [name] when another row shares the same name); [name] is the
 * plain Bluetooth name, used for routing and association so it stays the one the rest of the app knows.
 * [battery] is only set while [connected] is true, so the row never shows a reading as if it were
 * live; [lastSeenMillis], set only while not connected, is when that stale state was last updated.
 */
data class HomeRow(
    val address: String,
    val name: String,
    val model: String?,
    val connected: Boolean,
    val battery: String?,
    val lastSeenMillis: Long? = null,
    val label: String = name,
    /** The profile's `art` shape, which picks the row's thumbnail. */
    val art: String = "generic"
)

/**
 * The root screen: the paired FreeBuds the app detected, with the settings gear. Without the
 * Bluetooth permission or without paired FreeBuds it explains what to do instead.
 */
@Composable
fun HomeScreen(
    rows: List<HomeRow>,
    permissionMissing: Boolean,
    onOpenDevice: (HomeRow) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    StyledScaffold(
        title = stringResource(R.string.home_title),
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
            if (rows.isNotEmpty()) {
                StyledList {
                    rows.forEach { row ->
                        StyledListItem(
                            name = row.label,
                            description = rowDescription(row),
                            orientation = ListItemOrientation.Vertical,
                            leadingContent = { ProductThumbnail(row.art) },
                            onClick = { onOpenDevice(row) }
                        )
                    }
                }
            }
            if (permissionMissing) {
                Hint(
                    text = stringResource(R.string.bluetooth_permission_missing),
                    action = stringResource(R.string.open_app_info),
                    onAction = { openAppDetailsSettings(context) }
                )
            } else if (rows.isEmpty()) {
                Hint(
                    text = stringResource(R.string.no_freebuds_paired),
                    action = stringResource(R.string.open_bluetooth_settings),
                    onAction = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                )
            }
        }
    }
}

/**
 * The earbuds render on a rounded tile: darker neutral grey in the light theme, dark grey in the
 * dark theme, with a hairline outline, so a white product render stays clearly visible against the
 * near-white card behind it in light theme too.
 */
@Composable
private fun ProductThumbnail(art: String) {
    val dark = isSystemInDarkTheme()
    val tile = if (dark) Color(0xFF2C2C2E) else Color(0xFFD1D1D8)
    val outline = if (dark) Color(0x33FFFFFF) else Color(0x33000000)
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(tile, shape)
            .border(1.dp, outline, shape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(ProductArt.thumbnail(art)),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun rowDescription(row: HomeRow): String {
    val context = LocalContext.current
    val lastSeenText = row.lastSeenMillis?.let { stringResource(R.string.last_seen, formatUpdatedAt(context, it)) }
    return rowDescriptionText(
        model = row.model ?: stringResource(R.string.unknown_model),
        connectedLabel = stringResource(if (row.connected) R.string.buds_connected else R.string.not_connected),
        battery = row.battery,
        lastSeenText = lastSeenText,
    )
}

/**
 * The row's subtitle: model, connection state, and whichever one of [battery] (while connected) or
 * [lastSeenText] (while not, when a last known update time exists) fits; neither when there is no
 * last known state at all yet. Kept as a plain function (no [stringResource]) so it is unit testable.
 */
internal fun rowDescriptionText(model: String, connectedLabel: String, battery: String?, lastSeenText: String?): String =
    listOfNotNull(model, connectedLabel, battery ?: lastSeenText).joinToString(" · ")

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
