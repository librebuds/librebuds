// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
// Compact status presentation inspired by the GPL-3.0 QuickBuds battery panel.
// Independently implemented with LibreBuds' Compose state and model-specific artwork.
package io.github.librebuds.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.runtime.Composable
import io.github.librebuds.R
import io.github.librebuds.ui.model.Battery
import io.github.librebuds.ui.model.BatteryPart
import io.github.librebuds.ui.model.BatteryPartUi
import io.github.librebuds.ui.model.batteryParts

/**
 * One compact three-slot battery status panel. The status remains based on the
 * real FreeBuds battery reports; unreported values display an empty track and dash.
 */
@Composable
fun BatteryView(
    batteryList: List<Battery>,
    art: String = "generic",
    profileId: String? = null
) {
    val parts = batteryParts(batteryList)
    val ordered = listOf(BatteryPart.LEFT, BatteryPart.CASE, BatteryPart.RIGHT)
        .mapNotNull { kind -> parts.firstOrNull { it.part == kind } }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ordered.forEach { part ->
            BatterySlot(part, art, profileId, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BatterySlot(
    part: BatteryPartUi,
    art: String,
    profileId: String?,
    modifier: Modifier = Modifier
) {
    val progress = animateFloatAsState(
        targetValue = ((part.level ?: 0).coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 420),
        label = "FreeBuds battery"
    )
    val colors = MaterialTheme.colorScheme
    val track = colors.outlineVariant.copy(alpha = 0.48f)
    val accent = if (part.level != null && part.level <= 20) colors.error else colors.primary

    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(70.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(67.dp)) {
                val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )
                if (!part.missing) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.value,
                        useCenter = false,
                        style = stroke
                    )
                }
            }
            if (ProductArt.useTextOnlyBatteryArt(profileId)) {
                // Avoid showing a fictitious TWS earbud or case on a clip, neckband,
                // eyewear, watch-earbud or over-ear headset. Keep the real percentage.
                Text(
                    text = stringResource(part.shortLabel()),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else Image(
                painter = painterResource(ProductArt.part(part.part, art, profileId)),
                contentDescription = stringResource(part.imageLabel()),
                alpha = if (part.missing) 0.35f else 1f,
                // Keep the image layer centered independently of the charging badge.
                // The case artwork has visual weight below the center of its canvas.
                modifier = Modifier
                    .size(if (part.part == BatteryPart.CASE) 49.dp else 43.dp)
                    .offset(y = if (part.part == BatteryPart.CASE) (-4).dp else 0.dp),
                contentScale = ContentScale.Fit,
                colorFilter = if (profileId?.startsWith("pairfree-") == true) ColorFilter.tint(colors.onSurface) else null,
                alignment = Alignment.Center
            )
            if (part.charging) {
                // Keep the charging bolt readable and clear of the ring's progress stroke.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-2).dp, y = (-2).dp)
                        .size(20.dp)
                        .background(colors.surfaceContainerLow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_charging),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Text(
            text = stringResource(part.shortLabel()),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = part.level?.let { "$it%" } ?: "—",
            style = MaterialTheme.typography.titleLarge,
            color = if (part.missing) colors.onSurfaceVariant else colors.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

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
