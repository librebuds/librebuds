// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.bt

/**
 * Addresses the A2DP and headset profile proxies last reported as connected, per profile. Covers
 * what [AclTracker] misses after a process start, when no ACL broadcast has arrived yet.
 */
object AudioConnections {
    private val byProfile = mutableMapOf<Int, Set<String>>()

    /** Replaces what [profile] reported; each answer is a full list for that profile. */
    @Synchronized fun update(profile: Int, addresses: Collection<String>) {
        byProfile[profile] = addresses.mapTo(mutableSetOf()) { it.uppercase() }
    }

    /** Drops [address] from every profile, e.g. once its ACL link is gone. */
    @Synchronized fun forget(address: String) {
        byProfile.replaceAll { _, addresses -> addresses - address.uppercase() }
    }

    @Synchronized fun contains(address: String): Boolean = byProfile.values.any { address.uppercase() in it }
}

/**
 * Whether the phone still has the earbuds as an audio device: the question the controller asks
 * when the RFCOMM link drops, to tell "another device took the control channel" from "gone".
 */
fun isAudioConnected(address: String): Boolean = AclTracker.isConnected(address) || AudioConnections.contains(address)
