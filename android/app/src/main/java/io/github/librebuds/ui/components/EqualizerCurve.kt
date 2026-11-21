// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
// QuickBuds-inspired equalizer visualisation, independently implemented with Compose Canvas.
package io.github.librebuds.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** A read-only, live curve above the existing ten sliders and their PREVIEW/SAVE commands. */
@Composable
fun EqualizerCurve(gains: List<Int>, minGain: Int, maxGain: Int) {
    if (gains.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val animated = gains.mapIndexed { index, gain ->
        androidx.compose.runtime.key(index) {
            animateFloatAsState(gain.toFloat(), animationSpec = tween(180), label = "EQ band $index").value
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 6.dp, vertical = 8.dp)
        ) {
            val width = size.width
            val height = size.height
            val span = (maxGain - minGain).coerceAtLeast(1).toFloat()
            fun x(i: Int) = if (animated.size <= 1) width / 2f else width * i / (animated.size - 1)
            fun y(gain: Float) = height * (1f - (gain - minGain) / span)
            val grid = colors.outlineVariant.copy(alpha = 0.38f)
            repeat(5) { step ->
                val gy = height * step / 4f
                drawLine(grid, Offset(0f, gy), Offset(width, gy), strokeWidth = 1.dp.toPx())
            }
            val line = Path().apply {
                moveTo(x(0), y(animated.first()))
                for (i in 1 until animated.size) {
                    val middle = (x(i - 1) + x(i)) / 2f
                    cubicTo(middle, y(animated[i - 1]), middle, y(animated[i]), x(i), y(animated[i]))
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(x(animated.lastIndex), height)
                lineTo(x(0), height)
                close()
            }
            drawPath(
                fill,
                brush = Brush.verticalGradient(
                    colors = listOf(colors.primary.copy(alpha = 0.27f), colors.primary.copy(alpha = 0.01f))
                )
            )
            drawPath(line, color = colors.primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            animated.forEachIndexed { index, gain ->
                drawCircle(colors.surface, radius = 6.dp.toPx(), center = Offset(x(index), y(gain)))
                drawCircle(colors.primary, radius = 3.8.dp.toPx(), center = Offset(x(index), y(gain)))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("60 Hz", "500 Hz", "4 kHz", "16 kHz").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
