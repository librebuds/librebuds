// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.u8

/**
 * Earbud features that share the generic switch commands. [capability] is the TLV type the
 * ability query (2B/B3) answers for the feature (null: the feature has none and is only read),
 * [key] the feature key its read and write (2B/B4) carry on TLV 1.
 */
enum class Feature(val capability: Int?, val key: Int) {
    ADAPTIVE_VOLUME(0x03, 0x02),
    SINGLE_BUD_ANC(0x07, 0x05),
    DROP_DETECTION(0x09, 0x07),
    EAR_TIP(null, 0x08),
    HEAD_CONTROL(0x0B, 0x0B),
    AI_CONVERSATION(0x19, 0x1B),
    ;

    companion object {
        fun ofKey(key: Int): Feature? = entries.firstOrNull { it.key == key }
    }
}

/** One 2B/B4 answer: TLV 1 key, TLV 2 state, TLV 3 and 4 the feature's two extra values (head control: nod, shake). */
data class FeatureState(val key: Int, val state: Int?, val first: Int? = null, val second: Int? = null)

/** One 2B/B3 answer: whether the earbuds ask for the query again (TLV 1 = 1), and every capability TLV type with its first byte. */
data class FeatureAbilities(val needsReply: Boolean, val capabilities: Map<Int, Int>) {
    /** A feature is offered when its capability TLV is in the answer, whatever its value. */
    fun offers(feature: Feature): Boolean = feature.capability != null && feature.capability in capabilities
}

/**
 * The generic feature switch. The ability query (2B/B3) lists, after TLV 1 (1 on the first query,
 * 0 when answering the earbuds' request to ask again), every capability TLV the vendor app asks for
 * on a phone of another brand, byte for byte in its order. The answer carries a TLV for each
 * feature the model has. A feature's state is read and written with 2B/B4: TLV 1 = 1 byte key,
 * TLV 2 = the state; head control's nod and shake actions travel on TLV 3 and 4. The earbuds answer
 * a write with a 2B/B4 frame carrying the new state.
 */
object FeatureSwitch {
    val ABILITY = CommandId(0x2B, 0xB3)
    val SWITCH = CommandId(0x2B, 0xB4)

    /** Head control's TLV for the nod action, and for the shake action. */
    const val NOD = 3
    const val SHAKE = 4

    /** The capability TLVs of the vendor query, in its order (type to request value; null value = empty TLV). */
    private val QUERY: List<Pair<Int, Int?>> = listOf(
        0x0A to 0, 0x02 to 0, 0x03 to 0, 0x04 to 0, 0x09 to 0, 0x0B to 0, 0x0E to 0, 0x12 to 0,
        0x21 to null, 0x11 to null, 0x07 to 0, 0x14 to 1, 0x16 to 0, 0x15 to 0, 0x19 to 0, 0x25 to 0,
        0x29 to 0, 0x2A to null, 0x2B to null, 0x36 to 0,
    )

    fun abilityQuery(first: Boolean = true): Packet =
        Packet(ABILITY, listOf(Tlv.of(1, if (first) 1 else 0)) + QUERY.map { (type, value) -> if (value == null) Tlv.empty(type) else Tlv.of(type, value) })

    fun parseAbilities(packet: Packet): FeatureAbilities? {
        if (packet.id != ABILITY) return null
        val capabilities = packet.tlvs.filter { it.type != 1 && it.value.isNotEmpty() }.associate { it.type to it.value[0].u8() }
        return FeatureAbilities(needsReply = packet.find(1)?.firstOrNull()?.u8() == 1, capabilities = capabilities)
    }

    /** Head control is read with its key alone; every other feature asks for TLV 2. */
    fun read(feature: Feature): Packet =
        if (feature == Feature.HEAD_CONTROL) Packet(SWITCH, listOf(Tlv.of(1, feature.key))) else Packet(SWITCH, listOf(Tlv.of(1, feature.key), Tlv.empty(2)))

    fun write(feature: Feature, value: Int): Packet = writeField(feature, 2, value)

    /** Writes one of the feature's fields: TLV 2 (the state) or, for head control, [NOD] or [SHAKE]. */
    fun writeField(feature: Feature, field: Int, value: Int): Packet {
        require(field in 2..4) { "Feature field out of range: $field" }
        return Packet(SWITCH, listOf(Tlv.of(1, feature.key), Tlv.of(field, value)))
    }

    fun parseState(packet: Packet): FeatureState? {
        if (packet.id != SWITCH) return null
        val key = packet.find(1)?.takeIf { it.size == 1 }?.get(0)?.u8() ?: return null
        fun byte(type: Int) = packet.find(type)?.firstOrNull()?.u8()
        return FeatureState(key = key, state = byte(2), first = byte(3), second = byte(4))
    }

    /** Matches the 2B/B4 answer for [feature] (its key on TLV 1). */
    fun answers(feature: Feature, packet: Packet): Boolean = parseState(packet)?.key == feature.key
}
