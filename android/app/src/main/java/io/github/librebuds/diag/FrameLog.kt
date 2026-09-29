// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.diag

import io.github.librebuds.protocol.util.toHex

enum class FrameDirection { TX, RX }

/** Last [capacity] raw frames, exported in the same JSONL shape as test-vectors/. */
class FrameLog(private val capacity: Int = 2000, private val clock: () -> Long = System::currentTimeMillis) {
    private data class Entry(val ts: Long, val direction: FrameDirection, val hex: String)

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun record(direction: FrameDirection, bytes: ByteArray) {
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(Entry(clock(), direction, bytes.toHex()))
    }

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun toJsonl(): String = entries.joinToString("\n") {
        "{\"ts\":${it.ts},\"dir\":\"${it.direction.name.lowercase()}\",\"hex\":\"${it.hex}\"}"
    }
}
