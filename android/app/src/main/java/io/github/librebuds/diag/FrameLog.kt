// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.diag

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.frame.FrameReassembler
import io.github.librebuds.protocol.frame.LinkFrame
import io.github.librebuds.protocol.frame.RxEvent
import io.github.librebuds.protocol.util.toHex

enum class FrameDirection { TX, RX }

/**
 * Last [capacity] raw frames, in the JSONL shape of test-vectors/ plus a `"type":"frame"` field. The
 * buffer keeps the bytes as they went over the link; [toExportJsonl] is the redacted form for sharing.
 *
 * Every recorded chunk also goes through a per-direction [FrameReassembler], and each complete frame
 * is written to [logcat] as one line (`adb logcat -s LibreBudsFrames`), see [logLines].
 */
class FrameLog(
    private val capacity: Int = 2000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val logcat: (String) -> Unit = ::logToLogcat,
) {
    private class Entry(val ts: Long, val direction: FrameDirection, val bytes: ByteArray)

    private val entries = ArrayDeque<Entry>()
    private val reassemblers = FrameDirection.entries.associateWith { FrameReassembler() }

    @Synchronized
    fun record(direction: FrameDirection, bytes: ByteArray) {
        logLines(direction, bytes).forEach(logcat)
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(Entry(clock(), direction, bytes.copyOf()))
    }

    /**
     * The logcat lines for one chunk read from (or written to) the link: one `"RX 5A .."` line per
     * frame completed by this chunk, after reassembly (a frame split over reads is one line, a read
     * holding several frames gives several; fragmented messages are shown as one single frame).
     * Device info replies (01/07, serial numbers) and multipoint host rows and change pushes (2B/31,
     * 2B/36: MACs, host names) are left out.
     */
    private fun logLines(direction: FrameDirection, bytes: ByteArray): List<String> =
        reassemblers.getValue(direction).feed(bytes).mapNotNull { event ->
            if (event !is RxEvent.Payload) return@mapNotNull null
            val id = Packet.fromPayload(event.bytes)?.id
            val private = id in PRIVATE_BOTH_WAYS || (direction == FrameDirection.RX && id == DEVICE_INFO)
            if (private) null else "${direction.name} ${LinkFrame.encode(event.bytes).toHex()}"
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

    private companion object {
        val DEVICE_INFO = CommandId(0x01, 0x07)
        val PRIVATE_BOTH_WAYS = setOf(CommandId(0x2B, 0x31), CommandId(0x2B, 0x36))
    }
}

private fun logToLogcat(line: String) {
    try { android.util.Log.d("LibreBudsFrames", line) } catch (_: RuntimeException) { }
}
