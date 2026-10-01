// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.profile.Profile

/**
 * Level byte to send with a noise-control mode. Switching modes sends [AUTO_LEVEL]: the earbuds then
 * pick their own default level for the new mode, which is what the vendor app and OpenFreebuds do.
 * Explicit levels were acknowledged and even read back, but on FreeBuds 5 the earbuds did not
 * actually change the sound for awareness or cancellation. Re-selecting the current mode keeps its
 * level.
 */
@Suppress("UNUSED_PARAMETER")
fun levelFor(mode: AncMode, current: AncState?, profile: Profile): Int {
    if (current?.mode == mode) return current.level
    return AUTO_LEVEL
}

/** "Choose the level yourself", accepted for every mode in hardware tests. */
const val AUTO_LEVEL = 0xFF

