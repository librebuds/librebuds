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
    Modified for LibreBuds (2026): adapted to FreeBuds, labelled back button; see NOTICE.
*/

package io.github.librebuds.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.backdrops.LayerBackdrop
import io.github.librebuds.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.librebuds.ui.theme.DesignSystem
import io.github.librebuds.ui.theme.LocalDesignSystem
import io.github.librebuds.ui.theme.interFamily

/**
 * The screen frame with the top bar. With [showBackButton] the bar gets a back button calling
 * [onNavigateBack]: Material shows its usual arrow; the Apple style shows a round glass button, or,
 * when [backLabel] (the previous screen's title) is set, an iOS-style capsule with a chevron and that
 * label.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledScaffold(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    title: String,
    showBackButton: Boolean = false,
    onNavigateBack: () -> Unit = {},
    backLabel: String? = null,
    actionButtons: List<@Composable (backdrop: LayerBackdrop) -> Unit> = emptyList(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val hazeState = rememberHazeState(blurEnabled = true)

    when (LocalDesignSystem.current) {
        DesignSystem.Material -> {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
                    ) {
                        TopAppBar(
                            navigationIcon = {
                                if (showBackButton) {
                                    Row {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        FilledTonalIconButton(
                                            onClick = onNavigateBack,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Narrow)),
                                            shape = IconButtonDefaults.mediumRoundShape
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Default.ArrowBack,
                                                contentDescription = stringResource(R.string.navigate_back),
                                                modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                                            )
                                        }
                                    }
                                }
                            },
                            title = {
                                Crossfade(targetState = title) {
                                    Text(
                                        text = it,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = if (showBackButton) 8.dp else 12.dp, end = 12.dp),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            },
                            actions = {
                                actionButtons.forEach { actionButton ->
                                    actionButton(rememberLayerBackdrop())
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        )
                    }
                },
            ) { paddingValues ->
                Box(
                    modifier = modifier
                        .then(if (visible) Modifier.padding(paddingValues) else Modifier)
                        .fillMaxSize()
                        .hazeSource(hazeState)
                ) {
                    content()
                }
            }
        }
        DesignSystem.Apple -> {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                modifier = Modifier
                    .then(
                        if (!isDarkTheme) Modifier.shadow(
                            elevation = 36.dp,
                            shape = RoundedCornerShape(52.dp),
                            ambientColor = Color.Black,
                            spotColor = Color.Black
                        ) else Modifier
                    )
                    .clip(RoundedCornerShape(52.dp))
            ) { paddingValues ->
                val topPadding = paddingValues.calculateTopPadding()
                val startPadding = paddingValues.calculateLeftPadding(LocalLayoutDirection.current)
                val endPadding = paddingValues.calculateRightPadding(LocalLayoutDirection.current)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = startPadding, end = endPadding)
                ) {
                    val backdrop = rememberLayerBackdrop()
                    val bgColor = MaterialTheme.colorScheme.surfaceContainer
                    // The title stays clear of the back button and the actions, which can be wider
                    // than the round buttons (a labelled back capsule), so they are measured.
                    val density = LocalDensity.current
                    var backWidth by remember { mutableIntStateOf(0) }
                    var actionsWidth by remember { mutableIntStateOf(0) }
                    val titleStart = maxOf(72.dp, with(density) { backWidth.toDp() } + 8.dp)
                    val titleEnd = maxOf(72.dp, with(density) { actionsWidth.toDp() } + 8.dp)
                    AnimatedVisibility(
                        visible = showBackButton,
                        enter = fadeIn() + scaleIn(
                            initialScale = 0f,
                            animationSpec = tween()
                        ),
                        exit = fadeOut() + scaleOut(
                            targetScale = 0.5f,
                            animationSpec = tween(100)
                        ),
                        modifier = Modifier
                            .zIndex(3f)
                            .padding(top = topPadding, start = 8.dp)
                            .align(Alignment.TopStart)
                            .onSizeChanged { backWidth = it.width }
                    ) {
                        StyledIconButton(
                            onClick = onNavigateBack,
                            icon = if (backLabel != null) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.ArrowBack,
                            backdrop = backdrop,
                            label = backLabel,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }

                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn() + scaleIn(
                            initialScale = 0f,
                            animationSpec = tween()
                        ),
                        exit = fadeOut() + scaleOut(
                            targetScale = 0.5f,
                            animationSpec = tween(100)
                        ),
                        modifier = Modifier
                            .zIndex(2f)
                            .height(64.dp + topPadding)
                            .fillMaxWidth()
                            .layerBackdrop(backdrop)
                    ){
                        Box(
                            modifier = Modifier.hazeEffect(
                                state = hazeState,
                            ) {
                                backgroundColor = bgColor
                                tints = listOf(
                                    HazeTint(
                                        if (isDarkTheme) Color.Black.copy(0.55f) else Color(
                                            0xFFF2F2F7
                                        ).copy(alpha = 0.85f)
                                    )
                                )
                                blurRadius = 6.dp
                            }
                        ) {

                            Column(modifier = Modifier.fillMaxSize()) {
                                Spacer(modifier = Modifier.height(topPadding + 12.dp))
                                Crossfade(targetState = title) {
                                    val textColor = if (isDarkTheme) Color.White else Color.Black
                                    // Centred on the bar when it fits between the back button and the
                                    // actions; otherwise moved clear of them, and a long name ellipsizes.
                                    CenteredTitle(
                                        start = if (showBackButton) titleStart else 72.dp,
                                        end = if (actionButtons.isNotEmpty()) titleEnd else 72.dp
                                    ) {
                                        Text(
                                            text = it,
                                            style = TextStyle(
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = textColor,
                                                fontFamily = interFamily
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = visible && actionButtons.isNotEmpty(),
                        enter = fadeIn() + scaleIn(
                            initialScale = 0f,
                            animationSpec = tween()
                        ),
                        exit = fadeOut() + scaleOut(
                            targetScale = 0.5f,
                            animationSpec = tween(100)
                        ),
                        modifier = Modifier
                            .zIndex(3f)
                            .padding(top = topPadding, end = 8.dp)
                            .align(Alignment.TopEnd)
                            .onSizeChanged { actionsWidth = it.width }
                    ) {
                        Row{
                            actionButtons.forEach { actionButton ->
                                actionButton(backdrop)
                            }
                        }
                    }

                    Box(
                        modifier = modifier
                            .hazeSource(hazeState)
                            .fillMaxSize()
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Places [content] centred on the full width, but never over the [start] and [end] insets (the back
 * button and the actions): when centring would overlap one of them, it moves towards the other side,
 * and it is never wider than the space between them.
 */
@Composable
private fun CenteredTitle(start: Dp, end: Dp, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val startPx = start.roundToPx()
        val endPx = end.roundToPx()
        val room = (width - startPx - endPx).coerceAtLeast(0)
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, maxWidth = room))
        val x = ((width - placeable.width) / 2).coerceIn(startPx, (width - endPx - placeable.width).coerceAtLeast(startPx))
        layout(width, placeable.height) { placeable.placeRelative(x, 0) }
    }
}
