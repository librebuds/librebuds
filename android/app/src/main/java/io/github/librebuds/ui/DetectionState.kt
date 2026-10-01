// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.bt.isAudioConnected
import io.github.librebuds.bt.refreshAudioConnections
import io.github.librebuds.companion.AssociationStore
import io.github.librebuds.companion.DetectedBuds
import io.github.librebuds.companion.Presence
import io.github.librebuds.companion.detectFreeBuds
import io.github.librebuds.companion.readBondedDevices
import io.github.librebuds.companion.trackPresence
import kotlinx.coroutines.delay

/**
 * The paired FreeBuds and which of them are connected. [permissionMissing] means the list could not
 * be read at all. [settled] turns true once both audio profiles answered (or after a short wait), so
 * the connected set is trustworthy for the cold-start jump.
 */
data class Detection(
    val buds: List<DetectedBuds>,
    val connected: Set<String>,
    val permissionMissing: Boolean,
    val settled: Boolean,
) {
    fun isConnected(address: String): Boolean = address.uppercase() in connected
}

/** A2DP and headset, the profiles [refreshAudioConnections] asks. */
private const val AUDIO_PROFILE_COUNT = 2

/** How long the cold-start jump waits for the profile proxies; after that home just stays. */
private const val SETTLE_TIMEOUT_MILLIS = 2000L

private val WATCHED_ACTIONS = listOf(
    BluetoothDevice.ACTION_ACL_CONNECTED,
    BluetoothDevice.ACTION_ACL_DISCONNECTED,
    BluetoothDevice.ACTION_BOND_STATE_CHANGED,
    BluetoothDevice.ACTION_NAME_CHANGED,
    BluetoothDevice.ACTION_UUID,
    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
    BluetoothAdapter.ACTION_STATE_CHANGED,
)

/**
 * Reads the paired FreeBuds while the screen is resumed and follows pairing, connection and
 * Bluetooth on/off broadcasts, so the list updates without leaving the app.
 */
@Composable
fun rememberDetection(): Detection {
    val context = LocalContext.current
    val app = remember { LibreBudsApp.from(context) }
    val store = remember { AssociationStore(context) }
    var version by remember { mutableIntStateOf(0) }
    var settled by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        var answers = 0
        val refresh = {
            refreshAudioConnections(context) {
                if (++answers >= AUDIO_PROFILE_COUNT) settled = true
                version++
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val address = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.address
                // The manifest receiver records these too; doing it here as well does not depend on delivery order.
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> address?.let { trackPresence(Presence.APPEARED, it) }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> address?.let { trackPresence(Presence.DISAPPEARED, it) }
                }
                version++
                refresh()
            }
        }
        val filter = IntentFilter().apply { WATCHED_ACTIONS.forEach(::addAction) }
        // Only the system sends these protected broadcasts; exported just makes sure they are delivered.
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        version++
        refresh()
        onPauseOrDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(Unit) {
        delay(SETTLE_TIMEOUT_MILLIS)
        settled = true
    }

    return remember(version, settled) {
        val bonded = readBondedDevices(context)
        val buds = bonded?.let { detectFreeBuds(it, app.registry, store.known()) }.orEmpty()
        Detection(
            buds = buds,
            connected = buds.filter { isAudioConnected(it.address) }.mapTo(mutableSetOf()) { it.address.uppercase() },
            permissionMissing = bonded == null,
            // Without the permission nothing can connect-detect, so there is nothing to wait for.
            settled = settled || bonded == null,
        )
    }
}
