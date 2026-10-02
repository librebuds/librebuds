/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
    Modified for LibreBuds (2026): adapted to FreeBuds; see NOTICE.
*/

@file:OptIn(ExperimentalEncodingApi::class)

package io.github.librebuds.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.librebuds.R
import io.github.librebuds.ui.model.Battery
import io.github.librebuds.ui.model.BatteryComponent
import io.github.librebuds.ui.model.BatteryPart
import io.github.librebuds.ui.model.BatteryPartUi
import io.github.librebuds.ui.model.BatteryStatus
import io.github.librebuds.ui.model.batteryParts
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Left earbud, right earbud and case side by side, each with its own picture, ring and percentage.
 * A part with no level stays in place, dimmed, with a dash for its percentage.
 */
@Composable
fun BatteryView(
    batteryList: List<Battery>,
    art: String = "generic",
    profileId: String? = null
) {
    val parts = batteryParts(batteryList)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.widthIn(max = 500.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            for (part in parts) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(ProductArt.part(part.part, art, profileId)),
                        contentDescription = stringResource(part.imageLabel()),
                        alpha = if (part.missing) MISSING_ALPHA else 1f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(4.dp)
                    )

                    BatteryIndicator(
                        part.level,
                        if (part.charging) BatteryStatus.CHARGING else BatteryStatus.NOT_CHARGING,
                        prefix = stringResource(part.shortLabel())
                    )
                }
            }
        }
    }
}

private const val MISSING_ALPHA = 0.35f

private fun BatteryPartUi.imageLabel(): Int = when (part) {
    BatteryPart.LEFT -> R.string.bud_left_alt
    BatteryPart.RIGHT -> R.string.bud_right_alt
    BatteryPart.CASE -> R.string.case_alt
}

private fun BatteryPartUi.shortLabel(): Int = when (part) {
    BatteryPart.LEFT -> R.string.left
    BatteryPart.RIGHT -> R.string.right
    BatteryPart.CASE -> R.string.case_short
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BatteryViewPreview() {
    val fakeBattery = listOf(
        Battery(BatteryComponent.LEFT, 90, BatteryStatus.NOT_CHARGING),
        Battery(BatteryComponent.CASE, 60, BatteryStatus.NOT_CHARGING)
    )

    val bg = if (isSystemInDarkTheme()) Color.Black else Color(0xFFF2F2F7)

    Box(
        modifier = Modifier
            .background(bg)
            .padding(16.dp)
    ) {
        BatteryView(batteryList = fakeBattery)
    }
}
