// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv

/**
 * Sound quality / connectivity mode (0 = connectivity, 1 = quality). Read 2B/A3 (TLV 1
 * requested, reply value on TLV 2); write 2B/A2 TLV 1 = value.
 */
object SoundQuality {
    val GET = CommandId(0x2B, 0xA3)
    val SET = CommandId(0x2B, 0xA2)

    fun read(): Packet = Packet.read(GET, 1)

    fun write(value: Int): Packet = Packet(SET, listOf(Tlv.of(1, value)))

    fun parse(packet: Packet): Int? {
        if (packet.id != GET) return null
        val value = packet.find(2)?.takeIf { it.size == 1 } ?: return null
        return value[0].toInt()
    }
}
