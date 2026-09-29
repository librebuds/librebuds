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
 */
object Battery {
    val GET = CommandId(0x01, 0x08)
    val PUSH = CommandId(0x01, 0x27)

    fun request(): Packet = Packet.read(GET, 1, 2, 3)

    fun parse(packet: Packet): BatteryState? {
        if (packet.id != GET && packet.id != PUSH) return null
        val overall = packet.find(1)?.takeIf { it.size == 1 }?.get(0)?.u8()
        // SPEC-GAP: devices with a single battery report TLV 2 with one byte; treated as "no earbud levels".
        val levels = packet.find(2)?.takeIf { it.size == 3 }
        val charging = packet.find(3)?.takeIf { it.size == 3 }
        return BatteryState(
            overall = overall,
            left = levels?.get(0)?.u8(),
            right = levels?.get(1)?.u8(),
            case = levels?.get(2)?.u8(),
            leftCharging = charging?.get(0)?.let { it.u8() != 0 },
            rightCharging = charging?.get(1)?.let { it.u8() != 0 },
            caseCharging = charging?.get(2)?.let { it.u8() != 0 },
        )
    }
}
