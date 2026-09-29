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

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.librebuds.ui.theme.DesignSystem
import io.github.librebuds.ui.theme.LocalDesignSystem
import io.github.librebuds.ui.theme.interFamily


@Composable
fun StyledInputField(
    inputState: TextFieldState,
    focusRequester: FocusRequester,
    placeholder: String = "",
    singleLine: Boolean = true,
    forceApple: Boolean = false
) {
    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material && !forceApple

    if(m3eEnabled) {
        TextField(
            state = inputState,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
            },
            lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.Default,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
    else {
        val isDarkTheme = isSystemInDarkTheme()
        val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
        val textColor = if (isDarkTheme) Color.White else Color.Black
        val minHeight = if (singleLine) 58.dp else 120.dp
        val verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
        val hasText = inputState.text.isNotEmpty()
        val density = LocalDensity.current
        val spacerHeight by animateDpAsState(
            targetValue = if (hasText) with(density) { 32.sp.toDp() } else 0.dp,
            label = "labelSpacer"
        )

        val transition = updateTransition(hasText, label = "floating")
        val yOffset by transition.animateDp(label = "y") {
            if (it) with(density) { (-48).sp.toDp() } else 0.dp
        }

        Spacer(modifier = Modifier.height(spacerHeight))

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = verticalAlignment,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .background(
                        backgroundColor,
                        RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            focusRequester.requestFocus()
                        }
                    }
            ) {
                BasicTextField(
                    state = inputState,
                    lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.Default,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = textColor,
                        fontFamily = interFamily
                    ),
                    cursorBrush = SolidColor(textColor),
                    decorator = { innerTextField ->
                        Row(
                            modifier = Modifier.padding(top = if (singleLine) 0.dp else 16.dp),
                            verticalAlignment = verticalAlignment,
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f),
                                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
                                ) {
                                    Text(
                                        text = placeholder,
                                        style = TextStyle(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Light,
                                            fontFamily = interFamily,
                                            color = textColor.copy(alpha = 0.8f)
                                        ),
                                        modifier = Modifier
                                            .offset(y = yOffset)
                                    )

                                    innerTextField()
                                }
                            }
                            if (singleLine && !inputState.text.isEmpty()) {
                                IconButton(
                                    onClick = {
                                        inputState.clearText()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = null,
                                        tint = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color.Black.copy(
                                            alpha = 0.6f
                                        ),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp)
                        .focusRequester(focusRequester)
                )
            }
        }
    }
}
