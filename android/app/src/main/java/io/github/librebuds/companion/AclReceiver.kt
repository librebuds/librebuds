// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.companion

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import io.github.librebuds.bt.AclTracker
import io.github.librebuds.service.BudsService

/**
 * Tracks Bluetooth ACL links for [AclTracker] and, for the stored earbuds, starts or stops the
 * connection service when companion presence events do not arrive.
 */
class AclReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val address = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.address
            ?: return
        val presence = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> Presence.APPEARED.also { AclTracker.onConnected(address) }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> Presence.DISAPPEARED.also { AclTracker.onDisconnected(address) }
            else -> null
        }
        val stored = AssociationStore(context).primary()
        when (aclAction(presence, address, stored)) {
            PresenceAction.START -> stored?.let { BudsService.start(context, it.address, it.name) }
            PresenceAction.STOP -> BudsService.stop(context)
            PresenceAction.IGNORE -> Unit
        }
    }
}
