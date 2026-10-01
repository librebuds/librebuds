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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import io.github.librebuds.ui.model.BatteryStatus
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
fun BatteryView(
    batteryList: List<Battery>,
    art: String = "generic",
    profileId: String? = null
) {
    val left = batteryList.find { it.component == BatteryComponent.LEFT }
    val right = batteryList.find { it.component == BatteryComponent.RIGHT }
    val case = batteryList.find { it.component == BatteryComponent.CASE }

    val leftLevel = left?.level ?: 0
    val rightLevel = right?.level ?: 0
    val caseLevel = case?.level ?: 0

    val singleDisplayed = remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.widthIn(max = 500.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(ProductArt.battery(art, profileId)),
                    contentDescription = stringResource(R.string.buds),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .padding(8.dp)
                )

                if (
                    left?.status == right?.status &&
                    (leftLevel - rightLevel) in -3..3
                ) {
                    BatteryIndicator(
                        leftLevel.coerceAtMost(rightLevel),
                        left?.status ?: BatteryStatus.NOT_CHARGING
                    )
                    singleDisplayed.value = true
                } else {
                    singleDisplayed.value = false

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (leftLevel > 0 || left?.status != BatteryStatus.DISCONNECTED) {
                            BatteryIndicator(
                                leftLevel,
                                left?.status ?: BatteryStatus.NOT_CHARGING,
                                stringResource(R.string.left)
                            )
                        }

                        if (leftLevel > 0 && rightLevel > 0) {
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        if (rightLevel > 0 || right?.status != BatteryStatus.DISCONNECTED) {
                            BatteryIndicator(
                                rightLevel,
                                right?.status ?: BatteryStatus.NOT_CHARGING,
                                stringResource(R.string.right)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(ProductArt.case(profileId)),
                    contentDescription = stringResource(R.string.case_alt),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .padding(8.dp)
                )

                if (caseLevel > 0 || case?.status != BatteryStatus.DISCONNECTED) {
                    BatteryIndicator(
                        caseLevel,
                        case?.status ?: BatteryStatus.NOT_CHARGING,
                        prefix = if (!singleDisplayed.value) stringResource(R.string.case_short) else ""
                    )
                }
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BatteryViewPreview() {
    val fakeBattery = listOf(
        Battery(BatteryComponent.LEFT, 90, BatteryStatus.NOT_CHARGING),
        Battery(BatteryComponent.RIGHT, 40, BatteryStatus.CHARGING),
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
