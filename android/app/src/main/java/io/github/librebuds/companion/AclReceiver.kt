// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.companion

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import io.github.librebuds.bt.BtWorker
import io.github.librebuds.service.BudsService
import io.github.librebuds.state.PairHistory

/**
 * Tracks Bluetooth ACL links for [io.github.librebuds.bt.AclTracker], records when paired FreeBuds
 * connect ([PairHistory]) and makes sure the connection service runs when they do. The running service
 * listens for the same broadcasts itself; this receiver covers a service that is not running yet. The
 * system may refuse that start from the background, which [BudsService.start] logs. The paired devices
 * are read on [BtWorker] (binder calls), not on the main thread, while the broadcast is kept pending.
 */
class AclReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val address = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.address
            ?: return
        val presence = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> Presence.APPEARED
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> Presence.DISAPPEARED
            else -> null
        }
        trackPresence(presence, address)
        val pending = goAsync()
        val app = context.applicationContext
        BtWorker.execute {
            try {
                val buds = readDetectedBuds(app).orEmpty()
                val action = aclAction(presence, address) { candidate -> buds.any { it.address.equals(candidate, ignoreCase = true) } }
                when (action) {
                    PresenceAction.START -> {
                        PairHistory(app).record(address)
                        BudsService.start(app)
                    }
                    // Touches the controller, which lives on the main thread.
                    PresenceAction.GONE -> BtWorker.main { BudsService.onEarbudsGone(app) }
                    PresenceAction.IGNORE -> Unit
                }
            } finally {
                pending.finish()
            }
        }
    }
}
