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

package io.github.librebuds.ui.components


import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.librebuds.R
import io.github.librebuds.ui.model.BatteryStatus
import io.github.librebuds.ui.theme.LibreBudsTheme
import io.github.librebuds.ui.theme.interFamily

@Composable
fun BatteryIndicator(
    batteryPercentage: Int?,
    status: Int,
    prefix: String = "",
    previousCharging: Boolean = false,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val missing = batteryPercentage == null
    val level = batteryPercentage ?: 0
    val batteryTextColor = (if (isDarkTheme) Color.White else Color.Black).copy(alpha = if (missing) 0.45f else 1f)
    val batteryFillColor =
        if (level > 25) if (isDarkTheme) Color(0xFF2ED158) else Color(0xFF35C759)
        else if (isDarkTheme) Color(0xFFFC4244) else Color(0xFFfe373C)

    val initialScale = if (previousCharging) 1f else 0f
    val scaleAnim = remember { Animatable(initialScale) }
    val charging = !missing && status == BatteryStatus.CHARGING
    val targetScale = if (charging) 1f else 0f

    LaunchedEffect(previousCharging, charging) {
        scaleAnim.animateTo(targetScale, animationSpec = tween(durationMillis = 250))
    }

    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp), // just for haze to work
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.padding(bottom = 4.dp), contentAlignment = Alignment.Center
        ) {
            val strokeWidthPx = with(LocalDensity.current) { 4.dp.toPx() }

            val trackColor = if (isDarkTheme) Color(0xFF272728) else Color(0xFFE3E3E8)
            val progress = level / 100f

            Canvas(modifier = Modifier.size(34.dp)) {
                val startAngle = -90f
                val stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                val inset = strokeWidthPx / 2
                Rect(
                    left = inset,
                    top = inset,
                    right = size.width - inset,
                    bottom = size.height - inset
                )
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )

                if (!missing) drawArc(
                    color = batteryFillColor,
                    startAngle = startAngle,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = stroke
                )
            }

            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_charging),
                contentDescription = null,
                tint = batteryFillColor,
                modifier = Modifier.size(14.dp).scale(scaleAnim.value)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = listOf(prefix, if (missing) MISSING_LEVEL else "$level%").filter { it.isNotEmpty() }.joinToString(" "),
            color = batteryTextColor,
            style = TextStyle(
                fontSize = 14.sp,
                fontFamily = interFamily,
                textAlign = TextAlign.Center
            ),
        )
    }
}

/** Shown instead of a percentage when a part has no level. */
const val MISSING_LEVEL = "\u2014"

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BatteryIndicatorPreview() {
    LibreBudsTheme(m3eEnabled = false) {
        BatteryIndicator(
            batteryPercentage = 50,
            status = BatteryStatus.CHARGING,
            prefix = stringResource(R.string.left),
            previousCharging = false
        )
    }
}
