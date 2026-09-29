// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.profile.Profile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Level byte to send with a noise-control mode (see plan M2b Task 3 for the rule). */
fun levelFor(mode: AncMode, current: AncState?, profile: Profile): Int {
    if (current?.mode == mode) return current.level
    return when (mode) {
        AncMode.CANCELLATION -> if (DYNAMIC in cancellationLevels(profile)) DYNAMIC else 0
        AncMode.AWARENESS -> 0
        AncMode.OFF -> current?.level ?: 0
    }
}

private const val DYNAMIC = 3

private fun cancellationLevels(profile: Profile): List<Int> =
    (profile.capabilities["anc"]?.get("cancellationLevels") as? JsonArray)
        ?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList()
