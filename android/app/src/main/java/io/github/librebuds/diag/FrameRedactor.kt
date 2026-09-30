// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.diag

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.command.DeviceInfoCommand
import io.github.librebuds.protocol.command.Multipoint
import io.github.librebuds.protocol.frame.Crc16
import io.github.librebuds.protocol.frame.FrameReassembler
import io.github.librebuds.protocol.frame.LinkFrame

/**
 * Removes the earbuds' serial numbers, full Bluetooth addresses and host device names from logged
 * frames, for the diagnostics export only (the in-memory [FrameLog] stays raw).
 *
 * The entries of one direction are read as one byte stream, because an RX entry is a raw RFCOMM
 * read that may hold several frames or part of one. Every CRC-valid link frame found in the stream
 * is parsed (fragments are joined into their message first) and, for the commands below, patched
 * in place. Hidden values become 'X' bytes of the same length: every 01/07 field except the
 * allowlisted versions and model codes (3, 7, 10, 15) and the address (27), so the serial (9)
 * and the earbud serials (24) never leave; the 2B/31 host name (9); and 2B/36 records other than
 * 1- or 2-byte codes, since that push is not documented. Each address (01/07 TLV 27, 2B/31 TLV 4,
 * 2B/32 TLV 1, the address TLV of 2B/33, 6-byte 2B/36 records) keeps only the two bytes
 * [EventLog.maskMacs] keeps, the rest becoming 00. Addresses travel least-significant byte
 * first, so those are the first two bytes on the wire: 66 55 44 33 22 11 (11:22:33:44:55:66)
 * becomes 66 55 00 00 00 00, shown as 00:00:00:00:55:66. A value of any other length under an
 * address rule is zeroed completely. Lengths never change, so a patched frame gets a fresh
 * CRC and the stream is cut back into the original entries. An entry with any byte outside a
 * parsed frame (junk, a partial frame at the log's edges, an incomplete fragment series, a frame
 * over the reassembler's size limit) is not
 * exported at all: its bytes could hold anything.
 */
object FrameRedactor {
    /** One entry's export form: [bytes] null when it could not be parsed; [redacted] when bytes were replaced. */
    class Result(val bytes: ByteArray?, val redacted: Boolean)

    private class Frame(val start: Int, val size: Int) {
        val end: Int get() = start + size
    }

    private const val FLAG_FIRST = 1
    private const val FLAG_MIDDLE = 2
    private const val FLAG_LAST = 3
    private const val MIN_FRAME = 6
    private const val MIN_FRAGMENT = 7
    private const val MAC_SIZE = 6
    private const val MAC_KEPT = 2

    /** Redacts the [entries] of one direction, in the order they were logged; one result per entry. */
    fun redact(entries: List<ByteArray>): List<Result> {
        val stream = concat(entries.map { Triple(it, 0, it.size) })
        val out = stream.copyOf()
        val parsed = BooleanArray(stream.size)

        var group = mutableListOf<Frame>()
        for (frame in frames(stream)) {
            when (stream[frame.start + 3].toInt() and 0xFF) {
                LinkFrame.FLAG_SINGLE -> {
                    val payload = stream.copyOfRange(frame.start + 4, frame.end - 2)
                    val redacted = redactPayload(payload) ?: continue
                    if (!redacted.contentEquals(payload)) {
                        LinkFrame.encode(redacted).copyInto(out, destinationOffset = frame.start)
                    }
                    parsed.fill(true, frame.start, frame.end)
                }
                FLAG_FIRST, FLAG_MIDDLE, FLAG_LAST -> {
                    val flag = stream[frame.start + 3].toInt() and 0xFF
                    if (frame.size < MIN_FRAGMENT) continue
                    if (flag == FLAG_FIRST) group = mutableListOf()
                    val index = stream[frame.start + 4].toInt() and 0xFF
                    // Out of sequence: this frame and the series so far stay unparsed.
                    if (group.isEmpty() && flag != FLAG_FIRST || index != group.size) {
                        group = mutableListOf()
                        continue
                    }
                    group += frame
                    if (flag == FLAG_LAST) {
                        redactFragments(stream, out, group, parsed)
                        group = mutableListOf()
                    }
                }
                else -> Unit
            }
        }

        val results = ArrayList<Result>(entries.size)
        var offset = 0
        for (entry in entries) {
            val end = offset + entry.size
            val complete = (offset until end).all { parsed[it] }
            results += if (!complete) {
                Result(null, false)
            } else {
                val bytes = out.copyOfRange(offset, end)
                Result(bytes, !bytes.contentEquals(entry))
            }
            offset = end
        }
        return results
    }

    /** CRC-valid link frames in [stream], resyncing one byte at a time like the frame reassembler. */
    private fun frames(stream: ByteArray): List<Frame> {
        val found = mutableListOf<Frame>()
        var i = 0
        while (i + 3 <= stream.size) {
            if ((stream[i].toInt() and 0xFF) == LinkFrame.MAGIC) {
                val size = ((stream[i + 1].toInt() and 0xFF) shl 8 or (stream[i + 2].toInt() and 0xFF)) + 5
                if (size >= MIN_FRAME && size <= FrameReassembler.DEFAULT_MAX_FRAME && i + size <= stream.size && Crc16.xmodem(stream, i, i + size) == 0) {
                    found += Frame(i, size)
                    i += size
                    continue
                }
            }
            i++
        }
        return found
    }

    /** Joins a complete fragment series, redacts the message and writes it back fragment by fragment. */
    private fun redactFragments(stream: ByteArray, out: ByteArray, group: List<Frame>, parsed: BooleanArray) {
        val message = concat(group.map { Triple(stream, it.start + 5, it.end - 2) })
        val redacted = redactPayload(message) ?: return
        var at = 0
        for (frame in group) {
            val size = frame.size - 7
            if (!redacted.copyOfRange(at, at + size).contentEquals(message.copyOfRange(at, at + size))) {
                redacted.copyInto(out, destinationOffset = frame.start + 5, startIndex = at, endIndex = at + size)
                val crc = Crc16.xmodem(out, frame.start, frame.end - 2)
                out[frame.end - 2] = (crc ushr 8).toByte()
                out[frame.end - 1] = crc.toByte()
            }
            at += size
            parsed.fill(true, frame.start, frame.end)
        }
    }

    /** Joins the (array, from, until) slices into one array, sized once. */
    private fun concat(slices: List<Triple<ByteArray, Int, Int>>): ByteArray {
        val out = ByteArray(slices.sumOf { it.third - it.second })
        var at = 0
        for ((bytes, from, until) in slices) {
            bytes.copyInto(out, destinationOffset = at, startIndex = from, endIndex = until)
            at += until - from
        }
        return out
    }

    /**
     * [payload] (service, command, TLVs) with the sensitive values replaced, same length; the same
     * bytes for other commands; null when it is too short to be a packet.
     */
    fun redactPayload(payload: ByteArray): ByteArray? {
        val packet = Packet.fromPayload(payload) ?: return null
        val out = payload.copyOf()
        // Walks the records exactly as Tlv.parse does (clamped lengths), keeping their byte offsets.
        var i = 2
        while (i + 1 < out.size) {
            val type = out[i].toInt() and 0xFF
            val start = i + 2
            val end = minOf(start + (out[i + 1].toInt() and 0xFF), out.size)
            when (rule(packet.id, type, end - start)) {
                Rule.HIDE -> out.fill('X'.code.toByte(), start, end)
                Rule.MAC -> out.fill(0, if (end - start == MAC_SIZE) start + MAC_KEPT else start, end)
                Rule.KEEP -> Unit
            }
            i = end
        }
        return out
    }

    private enum class Rule { KEEP, HIDE, MAC }

    /** 01/07 fields exported as they are: platform/hardware (3), firmware (7) and the model codes (10, 15). */
    private val DEVICE_INFO_KEPT = setOf(3, 7, 10, 15)

    private fun rule(id: CommandId, type: Int, size: Int): Rule = when (id) {
        DeviceInfoCommand.GET -> when (type) {
            in DEVICE_INFO_KEPT -> Rule.KEEP
            27 -> Rule.MAC
            else -> Rule.HIDE
        }
        Multipoint.ENUMERATE -> when (type) {
            4 -> Rule.MAC
            9 -> Rule.HIDE
            else -> Rule.KEEP
        }
        Multipoint.PREFERRED -> if (type == 1) Rule.MAC else Rule.KEEP
        // The TLV type is the action code and the value the address.
        Multipoint.EXECUTE -> if (size == MAC_SIZE) Rule.MAC else Rule.KEEP
        // The push layout is not documented: 1- and 2-byte codes stay, a 6-byte record is taken as
        // an address, and anything else (a name, say) is hidden.
        Multipoint.CHANGED -> when {
            size <= 2 -> Rule.KEEP
            size == MAC_SIZE -> Rule.MAC
            else -> Rule.HIDE
        }
        else -> Rule.KEEP
    }
}
