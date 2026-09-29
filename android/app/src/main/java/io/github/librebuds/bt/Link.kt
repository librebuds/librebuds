// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.bt

import java.io.Closeable

/** A bidirectional byte stream to the earbuds (RFCOMM on Android, fakes in tests). */
interface Link : Closeable {
    /** Reads into [buffer]; returns the byte count, or -1 when the stream ended. */
    suspend fun read(buffer: ByteArray): Int

    suspend fun write(bytes: ByteArray)
}

fun interface LinkFactory {
    suspend fun open(address: String): Link
}
