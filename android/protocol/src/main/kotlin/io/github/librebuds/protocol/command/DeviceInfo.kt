package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.util.u8

/** Raw device-info fields keyed by TLV type, with accessors for the ones confirmed on devices. */
class DeviceInfo(val fields: Map<Int, ByteArray>) {
    val platform: String? get() = text(3)
    val firmware: String? get() = text(7)
    val serial: String? get() = text(9)

    // SPEC-GAP: both TLV 10 and 15 carried the SKU in tests; which one is canonical is unconfirmed.
    val sku: String? get() = text(10) ?: text(15)

    /** Classic Bluetooth address; the device sends it least-significant byte first. */
    val macAddress: String?
        get() = fields[27]?.takeIf { it.size == 6 }?.reversedArray()?.joinToString(":") { "%02X".format(it.u8()) }

    fun text(type: Int): String? =
        fields[type]?.toString(Charsets.UTF_8)?.trimEnd('\u0000')?.trim()?.takeIf { it.isNotEmpty() }
}

/** Device information query (01/07). */
object DeviceInfoCommand {
    val GET = CommandId(0x01, 0x07)
    val REQUESTED_TYPES = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 24, 27)

    fun request(): Packet = Packet.read(GET, *REQUESTED_TYPES)

    fun parse(packet: Packet): DeviceInfo? {
        if (packet.id != GET) return null
        return DeviceInfo(packet.tlvs.associate { it.type to it.value })
    }
}
