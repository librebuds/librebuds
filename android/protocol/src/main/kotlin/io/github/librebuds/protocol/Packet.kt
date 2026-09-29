package io.github.librebuds.protocol

import io.github.librebuds.protocol.frame.LinkFrame
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.u8

/** Service and command bytes that open every application payload. */
data class CommandId(val service: Int, val command: Int) {
    override fun toString(): String = "%02X/%02X".format(service, command)
}

/** Application payload: service, command, then TLV records. */
class Packet(val id: CommandId, val tlvs: List<Tlv>) {
    fun find(type: Int): ByteArray? = tlvs.firstOrNull { it.type == type }?.value

    fun toPayload(): ByteArray = byteArrayOf(id.service.toByte(), id.command.toByte()) + Tlv.encode(tlvs)

    fun toFrame(): ByteArray = LinkFrame.encode(toPayload())

    override fun toString(): String = "Packet($id, $tlvs)"

    companion object {
        fun fromPayload(payload: ByteArray): Packet? {
            if (payload.size < 2) return null
            return Packet(CommandId(payload[0].u8(), payload[1].u8()), Tlv.parse(payload, start = 2))
        }

        /** A read request asks for each field by sending its type with an empty value. */
        fun read(id: CommandId, vararg types: Int): Packet = Packet(id, types.map { Tlv.empty(it) })
    }
}
