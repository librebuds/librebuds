// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.u8

/**
 * One paired host from a 2B/31 enumeration reply. [connection] is the raw connection-state
 * code: 0 = not connected, any other positive value = connected, 9 = actively playing audio.
 * [mac] is upper-case `AA:BB:CC:DD:EE:FF`.
 * SPEC-GAP: the MAC byte order the device sends on this command is unconfirmed; device info
 * (01/07 TLV 27) sends its address reversed, but multipoint rows appear not to, so [mac] is
 * built in the byte order received, unreversed.
 */
data class HostRow(
    val index: Int,
    val count: Int,
    val mac: String,
    val name: String?,
    val connection: Int,
    val preferred: Boolean,
    val autoConnect: Boolean?,
) {
    val connected: Boolean get() = connection > 0
    val playing: Boolean get() = connection == 9
}

/** Action TLV type for 2B/33. Unpair (code 3) is intentionally not offered. */
enum class HostAction(val code: Int) {
    CONNECT(1),
    DISCONNECT(2),
    ENABLE_AUTO_CONNECT(4),
    DISABLE_AUTO_CONNECT(5),
}

/**
 * Multipoint: toggle (2B/2F read, 2B/2E write, both TLV 1 = 0/1), host enumeration
 * (2B/31, empty TLV 1 request, one reply packet per host), preferred host (2B/32 TLV 1 = MAC,
 * no ack), host action (2B/33, TLV type = the action code, value = MAC, no ack) and the
 * unsolicited change push (2B/36).
 */
object Multipoint {
    val TOGGLE_GET = CommandId(0x2B, 0x2F)
    val TOGGLE_SET = CommandId(0x2B, 0x2E)
    val ENUMERATE = CommandId(0x2B, 0x31)
    val PREFERRED = CommandId(0x2B, 0x32)
    val EXECUTE = CommandId(0x2B, 0x33)
    val CHANGED = CommandId(0x2B, 0x36)

    fun readToggle(): Packet = Packet.read(TOGGLE_GET, 1)

    fun parseToggle(packet: Packet): Boolean? {
        if (packet.id != TOGGLE_GET) return null
        val value = packet.find(1)?.takeIf { it.size == 1 } ?: return null
        return value[0].u8() != 0
    }

    fun writeToggle(enabled: Boolean): Packet = Packet(TOGGLE_SET, listOf(Tlv.of(1, if (enabled) 1 else 0)))

    fun enumerate(): Packet = Packet.read(ENUMERATE, 1)

    fun parseRow(packet: Packet): HostRow? {
        if (packet.id != ENUMERATE) return null
        // Count and index identify the row within the enumeration; without either one the row
        // can't be placed, so it's dropped (same treatment as a missing MAC).
        val count = packet.find(2)?.toSignedInt() ?: return null
        val index = packet.find(3)?.toSignedInt() ?: return null
        val macBytes = packet.find(4)?.takeIf { it.size == 6 } ?: return null
        return HostRow(
            index = index,
            count = count,
            mac = macBytes.toMacText(),
            name = packet.find(9)?.let { text(it) },
            connection = packet.find(5)?.toSignedInt() ?: 0,
            preferred = packet.find(7)?.toSignedInt() == 1,
            autoConnect = packet.find(8)?.toSignedInt()?.let { it == 1 },
        )
    }

    /** Throws [IllegalArgumentException] for a malformed [mac], as does [execute]. */
    fun setPreferred(mac: String): Packet = Packet(PREFERRED, listOf(Tlv(1, mac.macBytes())))

    fun execute(action: HostAction, mac: String): Packet = Packet(EXECUTE, listOf(Tlv(action.code, mac.macBytes())))

    fun isChange(packet: Packet): Boolean = packet.id == CHANGED

    private fun text(value: ByteArray): String? =
        value.toString(Charsets.UTF_8).trimEnd('\u0000').trim().takeIf { it.isNotEmpty() }

    /** Big-endian signed value of a 1- or 2-byte TLV; null for any other length (malformed). */
    private fun ByteArray.toSignedInt(): Int? = when (size) {
        1 -> this[0].toInt()
        2 -> (this[0].toInt() shl 8) or (this[1].toInt() and 0xFF)
        else -> null
    }

    private fun ByteArray.toMacText(): String = joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

    /** Throws [IllegalArgumentException] unless this is six colon-separated hex bytes. */
    private fun String.macBytes(): ByteArray {
        val parts = split(":")
        require(parts.size == 6 && parts.all { it.length == 2 }) { "Not a MAC address: $this" }
        return parts.map { it.toInt(16).toByte() }.toByteArray()
    }
}

/**
 * Accumulates 2B/31 enumeration replies (one packet per host) into a complete, index-sorted
 * list once as many distinct host indexes have arrived as [HostRow.count]. A duplicate index
 * overwrites the earlier row rather than counting twice. Each complete list starts a new burst,
 * and so does a row whose count differs from the rows held, so two bursts never mix. Rows that
 * cannot belong to any list (count below 1, index outside 0 until count) are ignored.
 */
class HostCollector {
    private val rows = mutableMapOf<Int, HostRow>()

    /** Adds one row; returns the complete sorted list once enough distinct indexes arrived, else null. */
    fun add(row: HostRow): List<HostRow>? {
        if (row.count <= 0 || row.index !in 0 until row.count) return null
        if (rows.values.any { it.count != row.count }) rows.clear()
        rows[row.index] = row
        if (rows.size < row.count) return null
        return partial().also { reset() }
    }

    fun partial(): List<HostRow> = rows.values.sortedBy { it.index }

    fun reset() = rows.clear()
}
