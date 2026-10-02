// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.u8

/** A preset the user made: slot [id] (100..102), ten band [gains] and its [name]. */
data class CustomPreset(val id: Int, val gains: List<Int>, val name: String)

/**
 * [active] preset id (unsigned), or null when the device did not report one; [available] built-in preset
 * ids in the device's order; [custom] the user's own presets.
 */
data class EqualizerState(val active: Int?, val available: List<Int>, val custom: List<CustomPreset> = emptyList())

/** What a custom-preset write asks for: try the gains without keeping them, keep them, or delete the slot. */
enum class EqOperation(val code: Int) { PREVIEW(0), SAVE(1), DELETE(2) }

/**
 * Equalizer presets. Read with 2B/4A requesting TLV 2 only (the vendor app's exact request;
 * FreeBuds 5 does not answer a request for TLV 1..8). The reply carries TLV 2 = active preset,
 * TLV 3 = list of available preset ids (0xFF entries are padding) and TLV 8 = the custom presets,
 * 36 bytes each (id, one byte, ten signed gains, a 24-byte name). The earbuds push the same frame
 * after every select. A built-in preset is selected with 2B/49 TLV 1 = preset id. Custom presets and
 * the extended presets (200 and up) are written with the full form: TLV 1 id, TLV 2 band count,
 * TLV 5 operation, TLV 3 gains, TLV 4 name (an extended preset's name is its id in ASCII). The
 * earbuds answer a write later with their own 2B/49 frame carrying a [Status] record;
 * [Status.SUCCESS] means it is applied (see [parseAck]).
 */
object Equalizer {
    val GET = CommandId(0x2B, 0x4A)
    val SET = CommandId(0x2B, 0x49)
    val EXTENDED = CommandId(0x2B, 0xA8)

    const val BANDS = 10
    const val MIN_GAIN = -60
    const val MAX_GAIN = 60
    const val MAX_NAME_LENGTH = 8
    val CUSTOM_IDS = 100..102
    private const val RECORD_SIZE = 36
    private const val NAME_OFFSET = 12

    fun read(): Packet = Packet.read(GET, 2)

    fun parse(packet: Packet): EqualizerState? {
        if (packet.id != GET) return null
        val active = packet.find(2)?.takeIf { it.size == 1 }?.get(0)?.u8()
        val available = packet.find(3)?.map { it.u8() }?.filter { it != 0xFF } ?: emptyList()
        return EqualizerState(active = active, available = available, custom = parseCustom(packet.find(8)))
    }

    /** The custom preset records of TLV 8; a trailing partial record is ignored. */
    fun parseCustom(value: ByteArray?): List<CustomPreset> {
        if (value == null) return emptyList()
        return (0 until value.size / RECORD_SIZE).map { index ->
            val start = index * RECORD_SIZE
            val gains = (start + 2 until start + NAME_OFFSET).map { value[it].toInt() }
            val name = value.copyOfRange(start + NAME_OFFSET, start + RECORD_SIZE).toString(Charsets.UTF_8).trimEnd('\u0000').trim()
            CustomPreset(id = value[start].u8(), gains = gains, name = name)
        }
    }

    fun select(preset: Int): Packet = Packet(SET, listOf(Tlv.of(1, preset)))

    /** The full write for a custom slot or an extended preset. */
    fun writeCustom(id: Int, gains: List<Int>, name: String, operation: EqOperation): Packet {
        require(gains.size == BANDS) { "Equalizer needs $BANDS gains" }
        require(gains.all { it in MIN_GAIN..MAX_GAIN }) { "Equalizer gain out of range" }
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        require(nameBytes.isNotEmpty() && nameBytes.size <= 24) { "Equalizer name length" }
        return Packet(
            SET,
            listOf(
                Tlv.of(1, id),
                Tlv.of(2, BANDS),
                Tlv.of(5, operation.code),
                Tlv(3, ByteArray(gains.size) { gains[it].toByte() }),
                Tlv(4, nameBytes),
            ),
        )
    }

    /** Selects an extended preset ([id] 200 and up) with the gains the vendor app ships for it. */
    fun selectExtended(id: Int, gains: List<Int>): Packet = writeCustom(id, gains, id.toString(), EqOperation.SAVE)

    /** Asks whether the extended presets are available (TLV 1 declaring one byte, sent bare as the vendor app does). */
    fun extendedQuery(): Packet = bareRead(EXTENDED)

    /** True when the 2B/A8 reply says the extended presets are available. */
    fun parseExtended(packet: Packet): Boolean? {
        if (packet.id != EXTENDED) return null
        if (Status.of(packet) != null && packet.find(1) == null) return false
        return packet.find(1)?.takeIf { it.size == 1 }?.get(0)?.u8()?.let { it == 1 }
    }

    /** True for a 2B/49 reply with [Status.SUCCESS], false for another status, null when it is not a select reply with a status. */
    fun parseAck(packet: Packet): Boolean? {
        if (packet.id != SET) return null
        return Status.of(packet)?.let { it == Status.SUCCESS }
    }

    /** The first custom slot no record uses, or null when all are taken. */
    fun freeSlot(custom: List<CustomPreset>): Int? = CUSTOM_IDS.firstOrNull { id -> custom.none { it.id == id } }
}
