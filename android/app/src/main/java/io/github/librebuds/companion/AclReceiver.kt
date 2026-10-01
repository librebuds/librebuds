// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.companion

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import io.github.librebuds.service.BudsService
import io.github.librebuds.state.PairHistory

/**
 * Tracks Bluetooth ACL links for [io.github.librebuds.bt.AclTracker], records when paired FreeBuds
 * connect ([PairHistory]) and makes sure the connection service runs when they do. The running service
 * listens for the same broadcasts itself; this receiver covers a service that is not running yet. The
 * system may refuse that start from the background, which [BudsService.start] logs.
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
        val buds = readDetectedBuds(context).orEmpty()
        val action = aclAction(presence, address) { candidate -> buds.any { it.address.equals(candidate, ignoreCase = true) } }
        when (action) {
            PresenceAction.START -> {
                PairHistory(context).record(address)
                BudsService.start(context)
            }
            PresenceAction.GONE -> BudsService.onEarbudsGone(context)
            PresenceAction.IGNORE -> Unit
        }
    }
}
