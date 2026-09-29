// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.u8

/**
 * Wear (in-ear) detection toggle. Read with 2B/11 (reply TLV 1 = 0/1), write with 2B/10
 * (TLV 1 = 0/1). The device also pushes an unsolicited in-ear report on 2B/03, parsed
 * separately by [parseInEar] since it is not a reply to either read or write.
 */
object WearDetection {
    val GET = CommandId(0x2B, 0x11)
    val SET = CommandId(0x2B, 0x10)
    private val PUSH = CommandId(0x2B, 0x03)

    fun read(): Packet = Packet.read(GET, 1)

    fun write(enabled: Boolean): Packet = Packet(SET, listOf(Tlv.of(1, if (enabled) 1 else 0)))

    fun parse(packet: Packet): Boolean? {
        if (packet.id != GET) return null
        val value = packet.find(1)?.takeIf { it.size == 1 } ?: return null
        return value[0].u8() != 0
    }

    // SPEC-GAP: which TLV type (8 or 9) carries the in-ear flag on the 2B/03 push is unconfirmed;
    // sources disagree, so we accept either one.
    fun parseInEar(packet: Packet): Boolean? {
        if (packet.id != PUSH) return null
        val value = (packet.find(8) ?: packet.find(9))?.takeIf { it.size == 1 } ?: return null
        return value[0].u8() != 0
    }
}
