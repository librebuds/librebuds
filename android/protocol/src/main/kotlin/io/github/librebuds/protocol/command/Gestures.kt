// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv

/** Read/write command ids for one gesture. */
enum class Gesture(val get: CommandId, val set: CommandId) {
    DOUBLE_TAP(CommandId(0x01, 0x20), CommandId(0x01, 0x1F)),
    TRIPLE_TAP(CommandId(0x01, 0x26), CommandId(0x01, 0x25)),
    LONG_PRESS(CommandId(0x2B, 0x17), CommandId(0x2B, 0x16)),
    NOISE_CYCLE(CommandId(0x2B, 0x19), CommandId(0x2B, 0x18)),
    SWIPE(CommandId(0x2B, 0x1F), CommandId(0x2B, 0x1E)),
}

/**
 * Gesture assignment. [left]/[right]/[inCall] hold the assigned action code (-1 = off);
 * [supported] lists the action codes the device reports it can assign.
 */
data class GestureSetting(val left: Int?, val right: Int?, val inCall: Int?, val supported: List<Int>)

/**
 * Per-gesture assignment. Read requests TLV 1 (left) and TLV 2 (right), plus TLV 4 (in-call)
 * when [read]'s withInCall is set; the device also returns TLV 3 (supported codes) unrequested.
 * Write sends TLV 1/2/4 for the ones being changed. Swipe has a single sensitivity value that
 * the device expects duplicated in TLV 1 and TLV 2, so its writer only takes [left].
 * All values are signed bytes (two's complement), -1 meaning "off".
 */
object Gestures {
    fun read(g: Gesture, withInCall: Boolean = false): Packet {
        val types = if (withInCall) intArrayOf(1, 2, 4) else intArrayOf(1, 2)
        return Packet.read(g.get, *types)
    }

    fun parse(g: Gesture, packet: Packet): GestureSetting? {
        if (packet.id != g.get) return null
        val left = packet.find(1)?.takeIf { it.size == 1 }?.get(0)?.toInt()
        val right = packet.find(2)?.takeIf { it.size == 1 }?.get(0)?.toInt()
        // SPEC-GAP: TLV 4 (in-call gesture) has not been confirmed on hardware.
        val inCall = packet.find(4)?.takeIf { it.size == 1 }?.get(0)?.toInt()
        val supported = packet.find(3)?.map { it.toInt() } ?: emptyList()
        return GestureSetting(left = left, right = right, inCall = inCall, supported = supported)
    }

    fun write(g: Gesture, left: Int? = null, right: Int? = null, inCall: Int? = null): Packet {
        val tlvs = mutableListOf<Tlv>()
        if (g == Gesture.SWIPE) {
            left?.let {
                tlvs += Tlv.of(1, it)
                tlvs += Tlv.of(2, it)
            }
        } else {
            left?.let { tlvs += Tlv.of(1, it) }
            right?.let { tlvs += Tlv.of(2, it) }
        }
        inCall?.let { tlvs += Tlv.of(4, it) }
        return Packet(g.set, tlvs)
    }
}
