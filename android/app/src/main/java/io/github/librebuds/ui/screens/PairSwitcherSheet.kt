// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import io.github.librebuds.ui.components.ProductArt
import io.github.librebuds.ui.components.StyledBottomSheet
import io.github.librebuds.ui.components.StyledList
import io.github.librebuds.ui.components.StyledListItem
import io.github.librebuds.ui.pairDescriptionText

/**
 * The pair switcher, opened from the device screen's title: the detected FreeBuds with their
 * connection state toward the phone, the one on screen marked. Picking one switches to it; the last
 * entry opens Bluetooth settings to pair another.
 */
@Composable
fun PairSwitcherSheet(visible: Boolean, pairs: List<PairRow>, onPick: (PairRow) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    StyledBottomSheet(visible = visible, onDismiss = onDismiss, backdrop = rememberLayerBackdrop()) { _, _ ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.switch_earbuds),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            StyledList {
                pairs.forEach { pair ->
                    StyledListItem(
                        name = pair.label,
                        description = pairDescription(pair),
                        orientation = ListItemOrientation.Vertical,
                        leadingContent = { ProductThumbnail(pair.art) },
                        trailingContent = if (pair.selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                        onClick = { onPick(pair) }
                    )
                }
                StyledListItem(
                    name = stringResource(R.string.pair_new_earbuds),
                    onClick = {
                        onDismiss()
                        openBluetoothSettings(context)
                    }
                )
            }
        }
    }
}

@Composable
private fun pairDescription(pair: PairRow): String {
    val context = LocalContext.current
    val lastSeenText = pair.lastSeenMillis?.let { stringResource(R.string.last_seen, formatUpdatedAt(context, it)) }
    return pairDescriptionText(
        model = pair.model ?: stringResource(R.string.unknown_model),
        connectedLabel = stringResource(if (pair.connected) R.string.buds_connected else R.string.not_connected),
        battery = pair.battery,
        lastSeenText = lastSeenText,
    )
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
            painter = painterResource(ProductArt.thumbnail(art)),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
    }
}
