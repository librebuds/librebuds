package io.github.librebuds.protocol.frame

/**
 * Link frame: 0x5A, big-endian u16 length (= payload size + 1), flag byte,
 * payload, CRC-16/XMODEM over everything before it (big-endian).
 * Requests are always small, so only the single-frame form (flag 0) is built.
 */
object LinkFrame {
    const val MAGIC = 0x5A
    const val FLAG_SINGLE = 0

    fun encode(payload: ByteArray): ByteArray {
        val length = payload.size + 1
        require(length <= 0xFFFF) { "Payload too long: ${payload.size}" }
        val out = ByteArray(payload.size + 6)
        out[0] = MAGIC.toByte()
        out[1] = (length ushr 8).toByte()
        out[2] = length.toByte()
        out[3] = FLAG_SINGLE.toByte()
        payload.copyInto(out, destinationOffset = 4)
        val crc = Crc16.xmodem(out, 0, out.size - 2)
        out[out.size - 2] = (crc ushr 8).toByte()
        out[out.size - 1] = crc.toByte()
        return out
    }
}
