// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.command.CustomPreset
import io.github.librebuds.protocol.command.PinchSlot
import io.github.librebuds.protocol.command.Side
import io.github.librebuds.protocol.command.Status
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.toHex

/**
 * Scripted answers for the feature commands, plugged into [FakeEarbuds.extra] (synthetic frames
 * shaped as the code-derived layouts say).
 *
 * [abilities] is the ability answer (capability TLV type to value; null = the earbuds stay silent),
 * [needsReply] its TLV 1 on the first answer. [features] maps a feature key to [state, first, second].
 * [pinch] maps each slot to (left, right). [ringing] holds each side's sound state (empty = the earbuds
 * do not answer find requests); [ringResult] is the result the earbuds give a ring or stop.
 * [refuse] lists command ids whose writes get the unsupported status (or, for 2B/B4, a reply carrying
 * the old state); [ignore] lists command ids whose writes are not applied (the reply still comes).
 * [customEqualizer] makes this fake own 2B/49 and 2B/4A, with custom preset records on TLV 8.
 */
class FeatureEarbuds(
    var abilities: Map<Int, Int>? = mapOf(0x07 to 1, 0x03 to 0, 0x19 to 0, 0x0B to 1),
    var needsReply: Boolean = false,
    val features: MutableMap<Int, IntArray> = mutableMapOf(
        0x05 to intArrayOf(1, 0, 0),
        0x02 to intArrayOf(0, 0, 0),
        0x1B to intArrayOf(0, 0, 0),
        0x0B to intArrayOf(1, 1, 2),
        0x08 to intArrayOf(1, 0, 0),
    ),
    val pinch: MutableMap<PinchSlot, Pair<Int, Int>> = mutableMapOf(
        PinchSlot(0, 1) to (0 to 0), PinchSlot(1, 1) to (1 to 1), PinchSlot(0, 2) to (2 to 2),
        PinchSlot(1, 2) to (4 to 4), PinchSlot(2, 2) to (3 to 3), PinchSlot(3, 0) to (6 to 5),
    ),
    var extended: Boolean? = true,
    var restReminder: Int? = 1,
    var hdCall: Int? = 0,
    var pickupMode: Int? = 1,
    val ringing: MutableMap<Side, Boolean> = mutableMapOf(Side.LEFT to false, Side.RIGHT to false),
    var ringResult: Int = 0,
    var awarenessSlider: Int = 5,
    val refuse: Set<String> = emptySet(),
    val ignore: Set<String> = emptySet(),
    var customEqualizer: Boolean = false,
    val custom: MutableList<CustomPreset> = mutableListOf(),
) {
    lateinit var base: FakeEarbuds

    /** Every request this fake answered or let pass, as payload hex exactly as sent. */
    val requests = mutableListOf<String>()

    /** Wires this fake into [earbuds] and returns them. */
    fun into(earbuds: FakeEarbuds): FakeEarbuds {
        base = earbuds
        earbuds.extra = ::answer
        return earbuds
    }

    private fun answer(request: Packet): List<Packet>? {
        requests += base.lastRequestHex
        val id = request.id.toString()
        fun applies() = id !in ignore && id !in refuse
        fun byte(type: Int) = request.find(type)?.takeIf { it.size == 1 }?.get(0)?.toInt()?.and(0xFF)
        return when (id) {
            "2B/B3" -> {
                val caps = abilities ?: return emptyList()
                val tlvs = listOf(Tlv.of(1, if (needsReply) 1 else 0)) + caps.map { (type, value) -> Tlv.of(type, value) }
                needsReply = false
                listOf(Packet(request.id, tlvs))
            }
            "2B/B4" -> {
                val key = byte(1) ?: return emptyList()
                val state = features[key] ?: return emptyList()
                val writes = (2..4).mapNotNull { field -> byte(field)?.let { field to it } }
                if (applies()) writes.forEach { (field, value) -> state[field - 2] = value }
                val tlvs = mutableListOf(Tlv.of(1, key), Tlv.of(2, state[0]))
                if (key == 0x0B) tlvs += listOf(Tlv.of(3, state[1]), Tlv.of(4, state[2]))
                listOf(Packet(request.id, tlvs))
            }
            "2B/93" -> {
                val slot = PinchSlot(byte(1) ?: return emptyList(), byte(2) ?: return emptyList())
                val (left, right) = pinch[slot] ?: return emptyList()
                listOf(Packet(request.id, listOf(Tlv.of(1, slot.type), Tlv.of(2, slot.scene), Tlv.of(3, left), Tlv.of(4, right))))
            }
            "2B/92" -> {
                val slot = PinchSlot(byte(1) ?: return emptyList(), byte(2) ?: return emptyList())
                val old = pinch[slot] ?: return emptyList()
                if (id in refuse) return listOf(status(request, Status.UNSUPPORTED))
                if (applies()) pinch[slot] = (byte(3) ?: old.first) to (byte(4) ?: old.second)
                // The vendor app does not judge this reply; the earbuds send none here.
                emptyList()
            }
            "2B/A8" -> extended?.let { listOf(Packet(request.id, listOf(Tlv.of(1, if (it) 1 else 0)))) } ?: listOf(status(request, Status.UNSUPPORTED))
            "2B/61" -> restReminder?.let { listOf(Packet(request.id, listOf(Tlv.of(1, it)))) } ?: emptyList()
            "2B/60" -> write(request, id) { restReminder = it }
            "2B/46" -> hdCall?.let { listOf(Packet(request.id, listOf(Tlv.of(1, it)))) } ?: emptyList()
            "2B/45" -> write(request, id) { hdCall = it }
            "2B/42" -> pickupMode?.let { listOf(Packet(request.id, listOf(Tlv.of(1, it)))) } ?: emptyList()
            "2B/41" -> write(request, id) { pickupMode = it }
            "2B/5E" -> {
                val side = byte(1)?.let(Side::of) ?: return emptyList()
                val on = ringing[side] ?: return emptyList()
                listOf(soundState(side, on))
            }
            "2B/5D" -> {
                val value = request.find(1)?.takeIf { it.size == 2 } ?: return emptyList()
                val side = Side.of(value[0].toInt().and(0xFF)) ?: return emptyList()
                if (side !in ringing) return emptyList()
                if (ringResult == 0 && applies()) ringing[side] = value[1].toInt().and(0xFF) == 0
                listOf(Packet(request.id, listOf(Tlv.of(2, side.code, ringResult))), soundState(side, ringing.getValue(side)))
            }
            "2B/2A" -> listOf(Packet(request.id, listOf(Tlv.of(1, base.ancLevel, base.ancMode), Tlv.of(2, awarenessSlider))))
            "2B/04" -> {
                val slider = byte(4) ?: return null
                if (applies()) {
                    base.ancMode = 2
                    base.ancLevel = 4
                    awarenessSlider = slider
                }
                listOf(Packet(request.id, listOf(Tlv.of(2, 0))))
            }
            "2B/49" -> if (customEqualizer) customWrite(request) else null
            "2B/4A" -> if (customEqualizer) listOf(equalizerState()) else null
            else -> null
        }
    }

    private fun write(request: Packet, id: String, apply: (Int) -> Unit): List<Packet> {
        if (id in refuse) return listOf(status(request, Status.UNSUPPORTED))
        if (id !in ignore) request.find(1)?.takeIf { it.size == 1 }?.let { apply(it[0].toInt().and(0xFF)) }
        return listOf(status(request, Status.SUCCESS))
    }

    private fun customWrite(request: Packet): List<Packet> {
        val id = request.find(1)?.get(0)?.toInt()?.and(0xFF) ?: return emptyList()
        val operation = request.find(5)?.get(0)?.toInt()?.and(0xFF)
        if (operation == null) {
            base.equalizerPreset = id
        } else {
            val gains = request.find(3)?.map { it.toInt() }.orEmpty()
            val name = request.find(4)?.toString(Charsets.UTF_8).orEmpty()
            when (operation) {
                1 -> {
                    if (id in 100..102) {
                        custom.removeAll { it.id == id }
                        custom += CustomPreset(id, gains, name)
                    }
                    base.equalizerPreset = id
                }
                2 -> custom.removeAll { it.id == id }
            }
        }
        return listOf(status(request, Status.SUCCESS), equalizerState())
    }

    private fun equalizerState(): Packet {
        val records = custom.sortedBy { it.id }.flatMap { preset ->
            listOf(preset.id.toByte(), 10.toByte()) + preset.gains.map { it.toByte() } + preset.name.toByteArray().copyOf(24).toList()
        }.toByteArray()
        val tlvs = mutableListOf(Tlv.of(2, base.equalizerPreset), Tlv.of(3, *base.equalizerPresets.toIntArray()))
        if (records.isNotEmpty()) tlvs += Tlv(8, records)
        return Packet(CommandId(0x2B, 0x4A), tlvs)
    }

    private fun soundState(side: Side, on: Boolean) = Packet(CommandId(0x2B, 0x5E), listOf(Tlv.of(2, side.code, if (on) 0 else 1)))

    private fun status(request: Packet, code: Int) =
        Packet(request.id, listOf(Tlv.of(Status.TYPE, (code shr 24) and 0xFF, (code shr 16) and 0xFF, (code shr 8) and 0xFF, code and 0xFF)))
}
