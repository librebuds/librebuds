// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv

/** [active] preset id (signed), or null when the device did not report one; [available] preset ids. */
data class EqualizerState(val active: Int?, val available: List<Int>)

/**
 * Equalizer presets. Read with 2B/4A requesting TLV 2 only (the vendor app's exact request;
 * FreeBuds 5 does not answer a request for TLV 1..8). The reply carries TLV 2 = active preset and
 * TLV 3 = list of available preset ids, and the earbuds push the same frame after every select. Write (select only, no custom preset editing)
 * with 2B/49 TLV 1 = preset id. The earbuds answer a select later with their own 2B/49 frame
 * carrying a [Status] record; [Status.SUCCESS] means the preset is applied (see [parseAck]).
 */
object Equalizer {
    val GET = CommandId(0x2B, 0x4A)
    val SET = CommandId(0x2B, 0x49)

    fun read(): Packet = Packet.read(GET, 2)

    fun parse(packet: Packet): EqualizerState? {
        if (packet.id != GET) return null
        val active = packet.find(2)?.takeIf { it.size == 1 }?.get(0)?.toInt()
        val available = packet.find(3)?.map { it.toInt() } ?: emptyList()
        return EqualizerState(active = active, available = available)
    }

    fun select(preset: Int): Packet = Packet(SET, listOf(Tlv.of(1, preset)))

    /** True for a 2B/49 reply with [Status.SUCCESS], false for another status, null when it is not a select reply with a status. */
    fun parseAck(packet: Packet): Boolean? {
        if (packet.id != SET) return null
        return Status.of(packet)?.let { it == Status.SUCCESS }
    }
}
