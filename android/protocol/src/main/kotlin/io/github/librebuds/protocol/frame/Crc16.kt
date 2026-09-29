package io.github.librebuds.protocol.frame

/**
 * CRC-16/XMODEM: polynomial 0x1021, initial value 0, no reflection, no final XOR.
 * The protocol stores it big-endian after the frame body.
 */
object Crc16 {
    private val TABLE = IntArray(256) { index ->
        var crc = index shl 8
        repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
        crc and 0xFFFF
    }

    internal fun tableHead(count: Int): List<Int> = TABLE.take(count)

    fun xmodem(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var crc = 0
        for (i in from until to) {
            val index = ((crc ushr 8) xor (data[i].toInt() and 0xFF)) and 0xFF
            crc = (TABLE[index] xor (crc shl 8)) and 0xFFFF
        }
        return crc
    }
}
