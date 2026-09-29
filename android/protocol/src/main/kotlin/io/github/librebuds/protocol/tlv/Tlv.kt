package io.github.librebuds.protocol.tlv

import io.github.librebuds.protocol.util.toHex
import io.github.librebuds.protocol.util.u8

/** One application-layer record: 1-byte type, 1-byte length, value. */
class Tlv(val type: Int, val value: ByteArray) {
    init {
        require(type in 0..255) { "TLV type out of range: $type" }
        require(value.size <= 255) { "TLV value too long: ${value.size}" }
    }

    override fun equals(other: Any?): Boolean =
        other is Tlv && other.type == type && other.value.contentEquals(value)

    override fun hashCode(): Int = 31 * type + value.contentHashCode()

    override fun toString(): String = "Tlv($type, ${value.toHex()})"

    companion object {
        fun empty(type: Int) = Tlv(type, ByteArray(0))

        fun of(type: Int, vararg bytes: Int) = Tlv(type, ByteArray(bytes.size) { bytes[it].toByte() })

        /**
         * Walks records from [start]. A declared length that runs past the end is clamped
         * to the available bytes instead of failing; a lone trailing type byte is ignored.
         */
        fun parse(data: ByteArray, start: Int = 0): List<Tlv> {
            val out = mutableListOf<Tlv>()
            var i = start
            while (i + 1 < data.size) {
                val type = data[i].u8()
                val length = data[i + 1].u8()
                val end = minOf(i + 2 + length, data.size)
                out += Tlv(type, data.copyOfRange(i + 2, end))
                i = end
            }
            return out
        }

        fun encode(tlvs: List<Tlv>): ByteArray {
            val out = ArrayList<Byte>()
            for (tlv in tlvs) {
                out += tlv.type.toByte()
                out += tlv.value.size.toByte()
                tlv.value.forEach { out += it }
            }
            return out.toByteArray()
        }
    }
}
