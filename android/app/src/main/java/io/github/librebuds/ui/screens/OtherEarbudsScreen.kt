// LibreBuds - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens

import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.foundation.layout.Row
import kotlin.math.roundToInt
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import io.github.librebuds.ui.other.NothingController
import io.github.librebuds.ui.other.NothingProtocol
import io.github.librebuds.ui.components.NoiseControlSettings
import io.github.librebuds.ui.components.BatteryView
import io.github.librebuds.ui.model.Battery
import io.github.librebuds.ui.model.BatteryComponent
import io.github.librebuds.ui.model.BatteryStatus
import io.github.librebuds.ui.model.NoiseControlMode
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.librebuds.R
import io.github.librebuds.ui.PairRow
import io.github.librebuds.ui.components.PremiumSegmentedTabs
import io.github.librebuds.ui.components.StyledButton
import io.github.librebuds.ui.components.StyledScaffold

/**
 * The existing QuickBuds-inspired component system, with an Android-level audio
 * status backend. Vendor commands remain unavailable until their protocol driver
 * is actually integrated and verified; avoid presenting fake ANC/EQ controls.
 */
@Composable
fun OtherEarbudsScreen(pair: PairRow, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var page by rememberSaveable(pair.address) { mutableIntStateOf(0) }
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember(audioManager) { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1 }
    var volume by remember(audioManager) { mutableFloatStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 0f) }
    val isNothing = pair.brand == "Nothing / CMF" ||
        pair.name.startsWith("Nothing ", ignoreCase = true) ||
        pair.name.startsWith("CMF ", ignoreCase = true)
    val controller = remember(context, pair.address, isNothing) {
        if (isNothing) NothingController(context.applicationContext, pair.address, pair.name) else null
    }
    val scope = rememberCoroutineScope()
    val nothingStatus = controller?.state?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(controller, pair.connected) {
        if (pair.connected) controller?.run() else controller?.close()
    }
    DisposableEffect(controller) {
        onDispose { controller?.close() }
    }
    val detailedBattery = nothingStatus?.battery?.let { data ->
        listOfNotNull(
            data[2]?.let { Battery(BatteryComponent.LEFT, it, BatteryStatus.NOT_CHARGING) },
            data[4]?.let { Battery(BatteryComponent.CASE, it, BatteryStatus.NOT_CHARGING) },
            data[3]?.let { Battery(BatteryComponent.RIGHT, it, BatteryStatus.NOT_CHARGING) }
        )
    }.orEmpty()

    StyledScaffold(
        title = pair.label,
        showBackButton = true,
        onNavigateBack = onNavigateBack,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(screenContentPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PremiumSegmentedTabs(
                labels = listOf(
                    stringResource(R.string.device_tab_home),
                    stringResource(R.string.device_tab_controls),
                    stringResource(R.string.device_tab_details)
                ),
                selected = page,
                onSelect = { page = it }
            )
            when (page) {
                0 -> {
                    if (pair.connected && detailedBattery.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.device_battery_status), style = MaterialTheme.typography.titleSmall)
                                BatteryView(detailedBattery)
                            }
                        }
                    } else DetailCard(
                        title = stringResource(R.string.device_battery_status),
                        description = if (pair.connected) {
                            pair.battery?.let { "${pair.name}: $it" }
                                ?: stringResource(R.string.other_battery_unknown)
                        } else stringResource(R.string.not_connected)
                    )
                    DetailCard(
                        title = stringResource(R.string.other_connected_audio),
                        description = stringResource(if (pair.connected) R.string.buds_connected else R.string.not_connected)
                    )
                    if (controller != null && pair.connected && nothingStatus != null) {
                        DetailCard(
                            title = stringResource(R.string.other_vendor_link),
                            description = nothingStatus.status + (nothingStatus.error?.let { " — $it" } ?: "")
                        )
                        if (nothingStatus.ready && nothingStatus.noise != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    NoiseControlSettings(
                                        selected = when (nothingStatus.noise) {
                                            NothingProtocol.OFF -> NoiseControlMode.OFF
                                            NothingProtocol.TRANSPARENCY -> NoiseControlMode.AWARENESS
                                            else -> NoiseControlMode.NOISE_CANCELLATION
                                        },
                                        modes = listOf(
                                            NoiseControlMode.OFF,
                                            NoiseControlMode.NOISE_CANCELLATION,
                                            NoiseControlMode.AWARENESS
                                        ),
                                        onSelected = { selected ->
                                            val mode = when (selected) {
                                                NoiseControlMode.OFF -> NothingProtocol.OFF
                                                NoiseControlMode.AWARENESS -> NothingProtocol.TRANSPARENCY
                                                NoiseControlMode.NOISE_CANCELLATION -> NothingProtocol.ANC_HIGH
                                            }
                                            scope.launch { controller.changeNoise(mode) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (controller == null || nothingStatus?.noise == null) {
                        DetailCard(
                            title = stringResource(R.string.other_controls_note_title),
                            description = stringResource(R.string.other_controls_note)
                        )
                    }
                }
                1 -> {
                    if (pair.connected) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.other_playback), style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        R.string.other_previous to KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                                        R.string.other_play_pause to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                        R.string.other_next to KeyEvent.KEYCODE_MEDIA_NEXT
                                    ).forEach { (label, keyCode) ->
                                        StyledButton(
                                            onClick = { 
                                                try {
                                                    audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                                                    audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                                                } catch (_: SecurityException) { /* Android controls media permissions. */ }
                                            },
                                            backdrop = rememberLayerBackdrop(),
                                            modifier = Modifier.weight(1f)
                                        ) { Text(stringResource(label), maxLines = 1) }
                                    }
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(stringResource(R.string.other_media_volume), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.other_media_volume_note), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Slider(
                                    value = volume.coerceIn(0f, maxVolume.toFloat()),
                                    onValueChange = { newValue ->
                                        volume = newValue
                                        try {
                                            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newValue.roundToInt(), 0)
                                        } catch (_: SecurityException) { /* System can restrict volume changes. */ }
                                    },
                                    valueRange = 0f..maxVolume.toFloat()
                                )
                            }
                        }
                    }
                    if (controller != null && nothingStatus?.ready == true && nothingStatus.eq != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.other_eq_presets), style = MaterialTheme.typography.titleSmall)
                                PremiumSegmentedTabs(
                                    labels = listOf("Balanced", "Voice", "Treble", "Bass"),
                                    selected = nothingStatus.eq.coerceIn(0, 3),
                                    onSelect = { scope.launch { controller.changeEq(it) } }
                                )
                            }
                        }
                    }
                    if (controller == null || nothingStatus?.eq == null) {
                        DetailCard(
                            title = stringResource(R.string.other_controls_note_title),
                            description = stringResource(R.string.other_controls_note)
                        )
                    }
                    StyledButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        backdrop = rememberLayerBackdrop(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.open_bluetooth_settings))
                    }
                }
                else -> {
                    DetailCard(
                        title = stringResource(R.string.model_name),
                        description = pair.model ?: pair.name
                    )
                    DetailCard(
                        title = stringResource(R.string.other_brand),
                        description = pair.brand ?: stringResource(R.string.other_generic)
                    )
                    DetailCard(
                        title = stringResource(R.string.other_support_source),
                        description = stringResource(R.string.other_pairfree_credit)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailCard(title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
