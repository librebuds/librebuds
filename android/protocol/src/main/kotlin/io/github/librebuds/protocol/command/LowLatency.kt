// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.u8

/**
 * Low-latency (gaming) mode. Single command 2B/6C for every direction, each request on its own:
 * [probe] asks for TLV 3, whose presence says the model has the setting (1 = it is the dynamic
 * variant, 0 = plain low latency; no TLV 3 = no setting); [read] asks for TLV 2 (0/1); [write]
 * sends TLV 1 = 0/1 and is answered with a [Status] record. [parse] only looks at TLV 2, so it
 * ignores the echoed write value on TLV 1.
 */
object LowLatency {
    val ID = CommandId(0x2B, 0x6C)

    fun read(): Packet = Packet.read(ID, 2)

    fun probe(): Packet = Packet.read(ID, 3)

    /** The probe's TLV 3 (0 = low latency, 1 = dynamic latency), or null when the reply has none. */
    fun parseSupport(packet: Packet): Int? {
        if (packet.id != ID) return null
        val value = packet.find(3)?.takeIf { it.size == 1 } ?: return null
        return value[0].u8()
    }

    fun write(enabled: Boolean): Packet = Packet(ID, listOf(Tlv.of(1, if (enabled) 1 else 0)))

    fun parse(packet: Packet): Boolean? {
        if (packet.id != ID) return null
        val value = packet.find(2)?.takeIf { it.size == 1 } ?: return null
        return value[0].u8() != 0
    }
}
