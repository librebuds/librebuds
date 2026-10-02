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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.librebuds.bt.AudioProfiles
import io.github.librebuds.bt.BtWorker
import io.github.librebuds.bt.CoalescedReader
import io.github.librebuds.bt.isAudioConnected
import io.github.librebuds.bt.probeAclConnections
import io.github.librebuds.companion.DetectedBuds
import io.github.librebuds.companion.Presence
import io.github.librebuds.companion.readDetectedBuds
import io.github.librebuds.companion.trackPresence
import io.github.librebuds.state.PairHistory

/**
 * The paired FreeBuds and which of them are connected to the phone, each from its own Bluetooth state
 * (ACL, A2DP or headset), not only the pair the controller holds. [permissionMissing] means the list
 * could not be read at all. [lastConnectedAt] is when each pair last connected, by uppercase address.
 */
data class Detection(
    val buds: List<DetectedBuds>,
    val connected: Set<String>,
    val permissionMissing: Boolean,
    val lastConnectedAt: Map<String, Long> = emptyMap(),
) {
    fun isConnected(address: String): Boolean = address.uppercase() in connected
}

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
 * Bluetooth on/off broadcasts, so the screen and the pair switcher update without leaving the app.
 * The first read happens during composition; later ones (paired devices and their names are binder
 * calls) run on [BtWorker], once per burst of broadcasts.
 */
@Composable
fun rememberDetection(): Detection {
    val context = LocalContext.current
    val history = remember { PairHistory(context) }
    var detection by remember { mutableStateOf(readDetection(context, history)) }
    val reader = remember {
        CoalescedReader(
            background = { BtWorker.execute { it.run() } },
            main = { BtWorker.main { it.run() } },
            read = {
                // An ACL-only link that came up before this process started is invisible to the broadcasts.
                readDetectedBuds(context)?.let { buds -> probeAclConnections(context, buds.map { it.address }) }
                readDetection(context, history)
            },
            deliver = { detection = it },
        )
    }

    LifecycleResumeEffect(Unit) {
        val onProfilesRead: () -> Unit = { reader.request() }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val address = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.address
                // The manifest receiver records these too; doing it here as well does not depend on delivery order.
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> address?.let { trackPresence(Presence.APPEARED, it) }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> address?.let { trackPresence(Presence.DISAPPEARED, it) }
                }
                reader.request()
                AudioProfiles.refresh(context)
            }
        }
        val filter = IntentFilter().apply { WATCHED_ACTIONS.forEach(::addAction) }
        // Only the system sends these protected broadcasts; exported just makes sure they are delivered.
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        AudioProfiles.addListener(onProfilesRead)
        reader.request()
        AudioProfiles.refresh(context)
        onPauseOrDispose {
            context.unregisterReceiver(receiver)
            AudioProfiles.removeListener(onProfilesRead)
        }
    }

    return detection
}

/** The paired FreeBuds and which are connected; binder calls and a preference read. */
private fun readDetection(context: Context, history: PairHistory): Detection {
    val detected = readDetectedBuds(context)
    val buds = detected.orEmpty()
    return Detection(
        buds = buds,
        connected = buds.filter { isAudioConnected(it.address) }.mapTo(mutableSetOf()) { it.address.uppercase() },
        permissionMissing = detected == null,
        lastConnectedAt = history.all(),
    )
}
