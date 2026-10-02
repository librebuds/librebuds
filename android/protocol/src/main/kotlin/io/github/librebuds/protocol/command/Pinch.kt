// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.u8

/** One pinch slot: [type] (0 single, 1 double, 2 triple, 3 pinch and hold) in [scene] (1 in a call, 2 not in a call, 0 for pinch and hold). */
data class PinchSlot(val type: Int, val scene: Int) {
    override fun toString(): String = "$type/$scene"
}

/** The actions assigned to one slot, unsigned ([Pinch.NONE] = no action); null when not reported. */
data class PinchSetting(val slot: PinchSlot, val left: Int?, val right: Int?)

/**
 * Pinch gestures on stem earbuds. Each slot is read on its own with 2B/93 (TLV 1 type, TLV 2
 * scene) and answered with the same TLVs plus TLV 3 (left action) and TLV 4 (right action). A write
 * (2B/92) names the slot and carries the left action on TLV 3 and the right on TLV 4; tap slots send
 * the same action to both earbuds in one frame, pinch and hold one side per frame. The vendor app
 * does not judge the write's reply, so a re-read of the slot confirms it.
 */
object Pinch {
    val SET = CommandId(0x2B, 0x92)
    val GET = CommandId(0x2B, 0x93)

    /** "No action". */
    const val NONE = 0xFF

    fun read(slot: PinchSlot): Packet = Packet(GET, listOf(Tlv.of(1, slot.type), Tlv.of(2, slot.scene)))

    fun write(slot: PinchSlot, left: Int?, right: Int?): Packet {
        require(left != null || right != null) { "Pinch write without a value" }
        val tlvs = mutableListOf(Tlv.of(1, slot.type), Tlv.of(2, slot.scene))
        left?.let { tlvs += Tlv.of(3, it) }
        right?.let { tlvs += Tlv.of(4, it) }
        return Packet(SET, tlvs)
    }

    fun parse(packet: Packet): PinchSetting? {
        if (packet.id != GET) return null
        fun byte(type: Int) = packet.find(type)?.takeIf { it.size == 1 }?.get(0)?.u8()
        val type = byte(1) ?: return null
        val scene = byte(2) ?: return null
        return PinchSetting(PinchSlot(type, scene), left = byte(3), right = byte(4))
    }

    /** Matches the 2B/93 answer for [slot]. */
    fun answers(slot: PinchSlot, packet: Packet): Boolean = parse(packet)?.slot == slot
}
