// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.u8

/**
 * Low-latency (gaming) mode. Single command 2B/6C for both directions: read requests TLV 2
 * (reply TLV 2 = 0/1), write sends TLV 1 = 0/1. [parse] only looks at TLV 2, so it ignores
 * the echoed write value on TLV 1.
 */
object LowLatency {
    val ID = CommandId(0x2B, 0x6C)

    fun read(): Packet = Packet.read(ID, 2)

    fun write(enabled: Boolean): Packet = Packet(ID, listOf(Tlv.of(1, if (enabled) 1 else 0)))

    fun parse(packet: Packet): Boolean? {
        if (packet.id != ID) return null
        val value = packet.find(2)?.takeIf { it.size == 1 } ?: return null
        return value[0].u8() != 0
    }
}
