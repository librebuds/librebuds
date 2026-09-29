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

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.librebuds.R
import io.github.librebuds.ui.model.NoiseControlMode
import io.github.librebuds.ui.theme.DesignSystem
import io.github.librebuds.ui.theme.LibreBudsTheme
import io.github.librebuds.ui.theme.LocalDesignSystem
import io.github.librebuds.ui.theme.sectionHeader
import kotlin.math.roundToInt

private data class NoiseControlOption(
    val mode: NoiseControlMode,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun NoiseControlSettings(
    selected: NoiseControlMode?,
    showOff: Boolean,
    onSelected: (NoiseControlMode) -> Unit
) {
    val options = buildList {
        if (showOff) {
            add(NoiseControlOption(NoiseControlMode.OFF, R.string.off, R.drawable.ic_mode_off))
        }
        add(NoiseControlOption(NoiseControlMode.NOISE_CANCELLATION, R.string.noise_cancellation, R.drawable.ic_mode_cancellation))
        add(NoiseControlOption(NoiseControlMode.AWARENESS, R.string.awareness, R.drawable.ic_mode_awareness))
    }
    // -1 when nothing is selected (mode unknown or OFF hidden): no option is highlighted.
    val selectedIndex = options.indexOfFirst { it.mode == selected }

    when (LocalDesignSystem.current) {
        DesignSystem.Material -> {
            Column {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.noise_control),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmallEmphasized
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    options.forEachIndexed { index, (mode, labelRes, iconRes) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            ToggleButton(
                                checked = selected == mode,
                                onCheckedChange = {
                                    if (it) {
                                        onSelected(mode)
                                    }
                                },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                colors = ToggleButtonDefaults.toggleButtonColors()
                                    .copy(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(42.dp)
                                )
                            }

                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        DesignSystem.Apple -> {
            val isDarkTheme = isSystemInDarkTheme()
            val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFE3E3E8)
            val textColor = if (isDarkTheme) Color.White else Color.Black
            val textColorSelected = if (isDarkTheme) Color.White else Color.Black
            val selectedBackground = if (isDarkTheme) Color(0xBF5C5A5F) else Color(0xFFFFFFFF)

            fun onModeSelected(mode: NoiseControlMode) {
                if (mode != selected) onSelected(mode)
            }

            // A divider is hidden when it touches the highlighted segment.
            fun dividerAlpha(dividerIndex: Int): Float =
                if (selectedIndex == dividerIndex || selectedIndex == dividerIndex + 1) 0f else 1f

            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.noise_control),
                    color = MaterialTheme.colorScheme.sectionHeader,
                    style = MaterialTheme.typography.labelSmallEmphasized
                )
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                val density = LocalDensity.current
                val buttonCount = options.size
                val buttonWidth = maxWidth / buttonCount

                val isDragging = remember { mutableStateOf(false) }
                var dragOffset by remember { mutableFloatStateOf(0f) }

                val animationSpec: AnimationSpec<Float> = SpringSpec(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = 0.01f
                )

                val targetOffset = buttonWidth * selectedIndex.coerceAtLeast(0)

                val animatedOffset by animateFloatAsState(
                    targetValue = with(density) {
                        if (isDragging.value) dragOffset else targetOffset.toPx()
                    },
                    animationSpec = animationSpec,
                    label = "selector"
                )

                @Composable
                fun ButtonRow(modifier: Modifier, includeSemantics: Boolean = true) {
                    Row(modifier = modifier.then(if (includeSemantics) Modifier else Modifier.clearAndSetSemantics {})) {
                        options.forEachIndexed { index, option ->
                            if (index > 0) {
                                VerticalDivider(
                                    thickness = 1.dp,
                                    modifier = Modifier
                                        .padding(vertical = 10.dp)
                                        .alpha(dividerAlpha(index - 1)),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                )
                            }
                            NoiseControlButton(
                                icon = ImageVector.vectorResource(option.iconRes),
                                label = stringResource(option.labelRes),
                                selected = selected == option.mode,
                                onClick = { onModeSelected(option.mode) },
                                textColor = if (selected == option.mode) textColorSelected else textColor,
                                modifier = Modifier.weight(1f),
                                usePadding = false
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(backgroundColor, RoundedCornerShape(28.dp))
                    ) {
                        // Underneath the animated selector; fully redrawn on top below, so it carries no
                        // accessibility semantics of its own (avoids duplicate TalkBack announcements).
                        ButtonRow(Modifier.fillMaxWidth(), includeSemantics = false)

                        if (selectedIndex >= 0) {
                            Box(
                                modifier = Modifier
                                    .width(buttonWidth)
                                    .fillMaxHeight()
                                    .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                                    .zIndex(0f)
                                    .draggable(
                                        orientation = Orientation.Horizontal,
                                        state = rememberDraggableState { delta ->
                                            dragOffset = (dragOffset + delta).coerceIn(
                                                0f,
                                                with(density) { (buttonWidth * (buttonCount - 1)).toPx() }
                                            )
                                        },
                                        onDragStarted = {
                                            dragOffset = with(density) { targetOffset.toPx() }
                                            isDragging.value = true
                                        },
                                        onDragStopped = {
                                            isDragging.value = false
                                            val position =
                                                dragOffset / with(density) { buttonWidth.toPx() }
                                            val newIndex = position.roundToInt().coerceIn(0, options.lastIndex)
                                            onModeSelected(options[newIndex].mode)
                                        }
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(3.dp)
                                        .background(selectedBackground, RoundedCornerShape(26.dp))
                                )
                            }
                        }

                        ButtonRow(
                            Modifier
                                .fillMaxWidth()
                                .zIndex(1f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        options.forEach { option ->
                            Text(
                                text = stringResource(option.labelRes),
                                style = TextStyle(fontSize = 12.sp, color = textColor),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Preview
@Composable
fun NoiseControlSettingsPreview() {
    LibreBudsTheme(
        m3eEnabled = true
    ) {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            NoiseControlSettings(
                selected = NoiseControlMode.NOISE_CANCELLATION,
                showOff = false,
                onSelected = { }
            )
        }
    }
}
