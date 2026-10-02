// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.u8

/** Earbud side as the find commands number it. */
enum class Side(val code: Int) {
    LEFT(0),
    RIGHT(1);

    companion object {
        fun of(code: Int): Side? = entries.firstOrNull { it.code == code }
    }
}

/** A find-earbuds report: [side] and its raw [result] (state reports: 0 = ringing; replies to ring or stop: 0 = done). */
data class RingReport(val side: Side, val result: Int)

/**
 * Making one earbud play a sound. Ring and stop are 2B/5D with TLV 1 = [side, 0 ring | 1 stop]; the
 * earbuds answer on 2B/5D and report each side's sound state on 2B/5E, both with TLV 2 = [side, value].
 * A state query asks 2B/5E with TLV 1 = side.
 */
object FindEarbuds {
    val SET = CommandId(0x2B, 0x5D)
    val STATE = CommandId(0x2B, 0x5E)

    fun ring(side: Side): Packet = Packet(SET, listOf(Tlv.of(1, side.code, 0)))

    fun stop(side: Side): Packet = Packet(SET, listOf(Tlv.of(1, side.code, 1)))

    fun query(side: Side): Packet = Packet(STATE, listOf(Tlv.of(1, side.code)))

    /** The state of [packet], a 2B/5E report: true while that side rings. */
    fun parseState(packet: Packet): Pair<Side, Boolean>? {
        if (packet.id != STATE) return null
        return report(packet)?.let { it.side to (it.result == 0) }
    }

    /** The answer to a ring or stop (2B/5D). */
    fun parseResult(packet: Packet): RingReport? = if (packet.id == SET) report(packet) else null

    /** Matches a 2B/5D or 2B/5E frame about [side]. */
    fun answers(side: Side, packet: Packet): Boolean = report(packet)?.side == side

    private fun report(packet: Packet): RingReport? {
        val value = packet.find(2)?.takeIf { it.size == 2 } ?: return null
        val side = Side.of(value[0].u8()) ?: return null
        return RingReport(side, value[1].u8())
    }
}
