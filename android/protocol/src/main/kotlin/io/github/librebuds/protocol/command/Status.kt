// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.util.u8

/**
 * Status record (TLV type 0x7F, four bytes, big-endian) that devices attach to many replies.
 *
 * Round-2 sweeps saw two values: [SUCCESS] and [UNSUPPORTED], the latter meaning the command
 * is understood but the model does not offer the feature. A model whose service does not know
 * the command at all answers nothing, so a caller can tell three outcomes apart: data, an
 * explicit refusal, or silence. That is what makes probing (rather than hand-written
 * capability lists) possible.
 */
object Status {
    const val TYPE = 0x7F
    const val SUCCESS = 100_000
    const val UNSUPPORTED = 100_003

    /** Status code carried by [packet], or null when it has no status record. */
    fun of(packet: Packet): Int? {
        val value = packet.find(TYPE)?.takeIf { it.size == 4 } ?: return null
        return (value[0].u8() shl 24) or (value[1].u8() shl 16) or (value[2].u8() shl 8) or value[3].u8()
    }

    fun isSuccess(packet: Packet): Boolean = of(packet) == SUCCESS

    fun isUnsupported(packet: Packet): Boolean = of(packet) == UNSUPPORTED
}
