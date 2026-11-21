// SPDX-License-Identifier: GPL-3.0-or-later
// Nothing framing adapted from Omega-Destroyer/PairFree GPLv3, source commit af534dca43627a9c76903fbb9a118ea4a783c856.
// Technical reference: https://github.com/Omega-Destroyer/PairFree/blob/main/docs/nothing/PROTOCOL.md
package io.github.librebuds.ui.other

/**
 * Nothing X / CMF RFCOMM protocol. The service UUID is vendor-specific, not
 * Android's standard SPP UUID. Commands and packet formats follow PairFree.
 */
object NothingProtocol {
    const val SERVICE_UUID = "AEAC4A03-DFF5-498F-843A-34487CF133EB"
    const val GET_PROTOCOL_VERSION = 0xC001
    const val GET_DEVICE_MODEL = 0xC01C
    const val GET_BATTERY = 0xC007
    const val GET_NOISE = 0xC01E
    const val GET_EQ_MODE = 0xC01F
    const val SET_NOISE = 0xF00F
    const val SET_EQ_MODE = 0xF010
    const val EVENT_BATTERY = 0xE001
    const val EVENT_NOISE = 0xE003
    const val OFF = 5
    const val ANC_HIGH = 1
    const val TRANSPARENCY = 7

    fun frame(command: Int, sequence: Int, payload: ByteArray = byteArrayOf(), crc: Boolean = true): ByteArray {
        require(payload.size <= 2048)
        val control = if (crc) 0x0160 else 0x0140
        val packet = byteArrayOf(
            0x55, control.toByte(), (control ushr 8).toByte(),
            command.toByte(), (command ushr 8).toByte(),
            payload.size.toByte(), (payload.size ushr 8).toByte(),
            sequence.toByte()
        ) + payload
        if (!crc) return packet
        val sum = crc16(packet)
        return packet + byteArrayOf(sum.toByte(), (sum ushr 8).toByte())
    }

    fun crc16(data: ByteArray): Int {
        var crc = 0xffff
        data.forEach { b ->
            crc = crc xor (b.toInt() and 0xff)
            repeat(8) { crc = if ((crc and 1) != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc
    }

    data class Response(val command: Int, val payload: ByteArray, val responseCode: Int) {
        val success: Boolean get() = responseCode == 0
    }

    class Parser {
        private var pending = ByteArray(0)
        fun feed(chunk: ByteArray): List<Response> {
            pending += chunk
            val results = mutableListOf<Response>()
            while (pending.isNotEmpty()) {
                val start = pending.indexOf(0x55.toByte())
                if (start == -1) { pending = byteArrayOf(); break }
                if (start > 0) pending = pending.copyOfRange(start, pending.size)
                if (pending.size < 8) break
                fun byte(at: Int) = pending[at].toInt() and 0xff
                val control = byte(1) or (byte(2) shl 8)
                val command = byte(3) or (byte(4) shl 8)
                val length = byte(5) or (byte(6) shl 8)
                if (length > 2048) { pending = pending.copyOfRange(1, pending.size); continue }
                val withCrc = control and 0x20 != 0
                // Reject implausible unprotected packets rather than accepting line noise.
                if (!withCrc && ((control and 0xF080) != 0 || ((control ushr 8) and 15) !in 1..2)) {
                    pending = pending.copyOfRange(1, pending.size); continue
                }
                val size = 8 + length + if (withCrc) 2 else 0
                if (pending.size < size) break
                if (withCrc) {
                    val got = byte(size - 2) or (byte(size - 1) shl 8)
                    if (got != crc16(pending.copyOfRange(0, size - 2))) {
                        pending = pending.copyOfRange(1, pending.size); continue
                    }
                }
                results += Response(command, pending.copyOfRange(8, 8 + length), control and 0x1f)
                pending = pending.copyOfRange(size, pending.size)
            }
            return results
        }
    }

    /** The low-level status payload is a list of part/level pairs, with a charging bit. */
    fun battery(payload: ByteArray): Map<Int, Int> {
        if (payload.isEmpty()) return emptyMap()
        val count = payload[0].toInt() and 0xff
        if (count !in 1..6 || payload.size < 1 + count * 2) return emptyMap()
        return (0 until count).mapNotNull { i ->
            val id = payload[1 + i * 2].toInt() and 0xff
            val percent = payload[2 + i * 2].toInt() and 0x7f
            if (id in listOf(2, 3, 4, 6) && percent in 0..100) id to percent else null
        }.toMap()
    }

    /** Noise group 1 = active mode. Group 2 = stored ANC strength. */
    fun noiseMode(payload: ByteArray): Int? {
        if (payload.isEmpty()) return null
        // Firmware often prefixes its three-byte group list with a count.
        val start = if (payload.size % 3 == 1) 1 else 0
        var p = start
        while (p + 2 < payload.size) {
            if (payload[p].toInt() and 0xff == 1) {
                val mode = payload[p + 1].toInt() and 0xff
                return mode.takeIf { it in 1..8 }
            }
            p += 3
        }
        return null
    }
}
