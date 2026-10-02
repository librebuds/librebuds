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
 * [supported] lists the action codes the device reports it accepts (TLV 3), [inCallSupported]
 * the in-call ones (TLV 6).
 */
data class GestureSetting(
    val left: Int?,
    val right: Int?,
    val inCall: Int?,
    val supported: List<Int>,
    val inCallSupported: List<Int> = emptyList(),
)

/** What the earbuds answered to a gesture write. */
enum class GestureAck { ACCEPTED, REJECTED }

/**
 * Per-gesture assignment. Read requests TLV 1 (left) and TLV 2 (right), plus TLV 4 (in-call)
 * when [read]'s withInCall is set; the device also returns TLV 3 (accepted codes) and, for
 * gestures with an in-call action, TLV 6 (accepted in-call codes) unrequested.
 *
 * A write changes one side per frame (TLV 1 left, TLV 2 right, TLV 4 in-call). Gestures with a
 * single value for both earbuds carry it in TLV 1 and TLV 2 of the same frame; swipe does that
 * with TLV 2 declaring two bytes for its one-byte value, which is exactly how the earbuds' own app
 * sends it on every model that has swipe, so it is reproduced byte for byte.
 * All values are signed bytes (two's complement), -1 meaning "off".
 *
 * The write's reply carries the result per side in TLV 3 (TLV 6 for in-call): 0 left applied,
 * 1 left refused, 2 right applied, 3 right refused (see [parseAck]).
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
        val inCall = packet.find(4)?.takeIf { it.size == 1 }?.get(0)?.toInt()
        val supported = packet.find(3)?.map { it.toInt() } ?: emptyList()
        val inCallSupported = packet.find(6)?.map { it.toInt() } ?: emptyList()
        return GestureSetting(left = left, right = right, inCall = inCall, supported = supported, inCallSupported = inCallSupported)
    }

    /**
     * The write frame. With [bothSides] (always for swipe) [left] is the single value for both
     * earbuds and [right] is ignored. Callers send one side per frame, as the earbuds' app does.
     */
    fun write(g: Gesture, left: Int? = null, right: Int? = null, inCall: Int? = null, bothSides: Boolean = false): Packet {
        val tlvs = mutableListOf<Tlv>()
        if (g == Gesture.SWIPE) {
            left?.let {
                tlvs += Tlv.of(1, it)
                tlvs += Tlv(2, byteArrayOf(it.toByte()), declaredLength = 2)
            }
        } else if (bothSides) {
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

    /**
     * The result of a write from its reply: [GestureAck.REJECTED] when any side reports a refusal
     * (1 or 3 in TLV 3 or TLV 6) or the reply carries a non-success [Status], [GestureAck.ACCEPTED]
     * when a side reports success (0 or 2), null when the reply says neither.
     */
    fun parseAck(g: Gesture, packet: Packet): GestureAck? {
        if (packet.id != g.set) return null
        val codes = packet.tlvs.filter { it.type == ACK_TYPE || it.type == IN_CALL_ACK_TYPE }.flatMap { tlv -> tlv.value.map { it.toInt() and 0xFF } }
        val status = Status.of(packet)
        return when {
            codes.any { it == LEFT_FAILED || it == RIGHT_FAILED } -> GestureAck.REJECTED
            status != null && status != Status.SUCCESS -> GestureAck.REJECTED
            codes.any { it == LEFT_OK || it == RIGHT_OK } -> GestureAck.ACCEPTED
            status == Status.SUCCESS -> GestureAck.ACCEPTED
            else -> null
        }
    }

    const val ACK_TYPE = 3
    const val IN_CALL_ACK_TYPE = 6
    const val LEFT_OK = 0
    const val LEFT_FAILED = 1
    const val RIGHT_OK = 2
    const val RIGHT_FAILED = 3
}
