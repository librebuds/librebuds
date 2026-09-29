// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.bt

/** Addresses with an active Bluetooth ACL link, fed by AclReceiver. Used to tell "taken over" from "gone". */
object AclTracker {
    private val connected = mutableSetOf<String>()

    @Synchronized fun onConnected(address: String) { connected += address.uppercase() }

    @Synchronized fun onDisconnected(address: String) { connected -= address.uppercase() }

    @Synchronized fun isConnected(address: String): Boolean = address.uppercase() in connected
}
