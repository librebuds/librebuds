// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.diag

import io.github.librebuds.protocol.util.toHex

enum class FrameDirection { TX, RX }

/**
 * Last [capacity] raw frames, in the JSONL shape of test-vectors/ plus a `"type":"frame"` field. The
 * buffer keeps the bytes as they went over the link; [toExportJsonl] is the redacted form for sharing.
 */
class FrameLog(private val capacity: Int = 2000, private val clock: () -> Long = System::currentTimeMillis) {
    private class Entry(val ts: Long, val direction: FrameDirection, val bytes: ByteArray)

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun record(direction: FrameDirection, bytes: ByteArray) {
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(Entry(clock(), direction, bytes.copyOf()))
    }

    @Synchronized
    fun size(): Int = entries.size

    /** The raw frames, unredacted. */
    @Synchronized
    fun toJsonl(): String = entries.joinToString("\n") { line(it, it.bytes.toHex(), redacted = false) }

    /**
     * The frames with serial numbers, full addresses and host names removed ([FrameRedactor]); a changed line
     * gets `"redacted":true`, and an entry that could not be parsed is exported as `"unparsed"` with
     * its length instead of its bytes.
     */
    fun toExportJsonl(): String {
        // Redaction walks the whole log; only the copy is taken under the lock, so frames keep being recorded meanwhile.
        val snapshot = synchronized(this) { entries.toList() }
        val results = HashMap<Entry, FrameRedactor.Result>()
        for (direction in FrameDirection.entries) {
            val stream = snapshot.filter { it.direction == direction }
            stream.zip(FrameRedactor.redact(stream.map { it.bytes })).forEach { (entry, result) -> results[entry] = result }
        }
        return snapshot.joinToString("\n") { entry ->
            val result = results.getValue(entry)
            val bytes = result.bytes
            if (bytes == null) {
                "{\"type\":\"frame\",\"ts\":${entry.ts},\"dir\":\"${entry.direction.name.lowercase()}\",\"hex\":\"unparsed\",\"length\":${entry.bytes.size}}"
            } else {
                line(entry, bytes.toHex(), result.redacted)
            }
        }
    }

    private fun line(entry: Entry, hex: String, redacted: Boolean): String =
        "{\"type\":\"frame\",\"ts\":${entry.ts},\"dir\":\"${entry.direction.name.lowercase()}\",\"hex\":\"$hex\"" +
            (if (redacted) ",\"redacted\":true" else "") + "}"
}
