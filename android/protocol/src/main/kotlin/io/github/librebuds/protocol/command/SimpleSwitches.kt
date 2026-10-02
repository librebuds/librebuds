// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.u8

/**
 * A one-byte setting with its own read and write commands. The read asks with [readRequest] and is
 * answered with the value on TLV 1; the write sends TLV 1 = value and is answered with a [Status]
 * record, [Status.SUCCESS] meaning applied.
 */
open class ByteSwitch internal constructor(val get: CommandId, val set: CommandId, private val readRequest: () -> Packet) {
    fun read(): Packet = readRequest()

    fun write(value: Int): Packet = Packet(set, listOf(Tlv.of(1, value)))

    fun parse(packet: Packet): Int? {
        if (packet.id != get) return null
        return packet.find(1)?.takeIf { it.size == 1 }?.get(0)?.u8()
    }

    /** True for a [Status.SUCCESS] reply to the write, false for another status, null when [packet] is not such a reply. */
    fun parseAck(packet: Packet): Boolean? {
        if (packet.id != set) return null
        return Status.of(packet)?.let { it == Status.SUCCESS }
    }
}

/** Rest reminder on/off: read 2B/61 asking for TLV 1, write 2B/60 (1 on, 0 off). */
object RestReminder : ByteSwitch(CommandId(0x2B, 0x61), CommandId(0x2B, 0x60), { Packet.read(CommandId(0x2B, 0x61), 1) })

/**
 * The read the vendor app sends for some settings: TLV 1 declaring one byte but carrying none
 * (the payload ends after `01 01`), reproduced byte for byte.
 */
internal fun bareRead(id: CommandId): Packet = Packet(id, listOf(Tlv(1, ByteArray(0), declaredLength = 1)))

/** HD calls on/off: read 2B/46 (see [bareRead]), write 2B/45 (1 on, 0 off). */
object HdCall : ByteSwitch(CommandId(0x2B, 0x46), CommandId(0x2B, 0x45), { bareRead(CommandId(0x2B, 0x46)) })

/** Recording (pickup) mode: read 2B/42 (see [bareRead]), write 2B/41 ([VOICES] or [SURROUNDINGS]). */
object PickupMode : ByteSwitch(CommandId(0x2B, 0x42), CommandId(0x2B, 0x41), { bareRead(CommandId(0x2B, 0x42)) }) {
    const val SURROUNDINGS = 0
    const val VOICES = 1
}
