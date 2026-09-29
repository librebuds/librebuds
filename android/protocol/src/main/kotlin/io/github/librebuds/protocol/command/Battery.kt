package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.util.u8

/** Battery levels in percent; null when the device did not report the field. */
data class BatteryState(
    val overall: Int?,
    val left: Int?,
    val right: Int?,
    val case: Int?,
    val leftCharging: Boolean?,
    val rightCharging: Boolean?,
    val caseCharging: Boolean?,
)

/**
 * Battery query (01/08) and unsolicited battery report (01/27).
 * TLV 1: overall level. TLV 2: levels [left, right, case]. TLV 3: charging flags, same order.
 *
 * The TLV 2 order is confirmed by round-2 captures: FreeBuds 5 reported 64 64 59 and
 * FreeBuds Pro 3 reported 64 64 2D with both buds full inside a partly discharged case,
 * so the third byte is the case. An earlier analysis claiming [case, left, right] was wrong.
 *
 * Charging flags use bit 7 of each byte on some models and a plain 0/1 on others, so any
 * non-zero value counts as charging.
 */
object Battery {
    val GET = CommandId(0x01, 0x08)
    val PUSH = CommandId(0x01, 0x27)

    /** Types 4..6 are reported by newer models only; their meaning is still unknown. */
    fun request(): Packet = Packet.read(GET, 1, 2, 3)

    /** Extended read used during capability probing; newer models add TLV 4..6. */
    fun extendedRequest(): Packet = Packet.read(GET, 1, 2, 3, 4, 5, 6)

    fun parse(packet: Packet): BatteryState? {
        if (packet.id != GET && packet.id != PUSH) return null
        val overall = packet.find(1)?.takeIf { it.size == 1 }?.get(0)?.u8()
        // SPEC-GAP: devices with a single battery report TLV 2 with one byte; treated as "no earbud levels".
        val levels = packet.find(2)?.takeIf { it.size >= 3 }
        val charging = packet.find(3)?.takeIf { it.size >= 3 }
        fun ByteArray.charging(index: Int): Boolean = this[index].u8() != 0
        return BatteryState(
            overall = overall,
            left = levels?.get(0)?.u8(),
            right = levels?.get(1)?.u8(),
            case = levels?.get(2)?.u8(),
            leftCharging = charging?.charging(0),
            rightCharging = charging?.charging(1),
            caseCharging = charging?.charging(2),
        )
    }
}
