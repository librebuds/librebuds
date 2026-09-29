package io.github.librebuds.protocol.util

/** Parses "5A 00 09" or "5a0009" into bytes. Whitespace is ignored. */
fun String.hexToBytes(): ByteArray {
    val clean = filterNot { it.isWhitespace() }
    require(clean.length % 2 == 0) { "Odd number of hex digits: $this" }
    return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

/** Formats bytes as upper-case hex pairs separated by single spaces. */
fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

/** Unsigned value of a byte (0..255). */
internal fun Byte.u8(): Int = toInt() and 0xFF
