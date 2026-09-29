// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.companion

import io.github.librebuds.protocol.profile.ProfileRegistry

data class BondedDevice(val name: String?, val address: String)

/** [name] falls back to the address for display; [bondedName] is the Bluetooth name, if any. */
data class Candidate(val name: String, val address: String, val profileId: String, val known: Boolean, val bondedName: String? = null)

/** Paired devices for the "add earbuds" list: recognised models first, then alphabetically. */
fun candidates(bonded: List<BondedDevice>, registry: ProfileRegistry): List<Candidate> =
    bonded.map { device ->
        val profile = registry.match(btName = device.name)
        Candidate(device.name ?: device.address, device.address, profile.id, profile.id != ProfileRegistry.GENERIC.id, device.name)
    }.sortedWith(compareByDescending<Candidate> { it.known }.thenBy { it.name.lowercase() })
