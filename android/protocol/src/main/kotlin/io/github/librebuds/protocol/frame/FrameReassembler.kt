package io.github.librebuds.protocol.frame

import io.github.librebuds.protocol.util.u8

sealed interface RxEvent {
    /** A verified application payload (service, command, TLVs). */
    class Payload(val bytes: ByteArray) : RxEvent

    /** Bytes that were discarded, with the reason. Useful for diagnostics logs. */
    class Dropped(val reason: DropReason, val bytes: ByteArray) : RxEvent
}

enum class DropReason { JUNK, FOREIGN_DIALECT, BAD_LENGTH, BAD_CRC, UNKNOWN_FLAG, FRAGMENT_SEQUENCE }

/**
 * Turns arbitrary RFCOMM read chunks into verified payloads.
 *
 * - Frames start with 0x5A; total size is the big-endian u16 at bytes 1-2 plus 5.
 * - Non-0x5A leading bytes are skipped to the next 0x5A (0x7F/0x89/0x8A leaders are other
 *   framings on the same channel, unsupported here, and reported as FOREIGN_DIALECT).
 * - The CRC over a whole valid frame, CRC bytes included, is 0. On a bad CRC or impossible
 *   length one byte is dropped and parsing resyncs; the [RxEvent.Dropped] event carries only
 *   that one discarded byte, not the whole candidate frame.
 * - Flag 0 carries the payload at offset 4. Flags 1/2/3 (first/middle/last fragment) carry a
 *   fragment index at offset 4 and data from offset 5; indexes must arrive as 0, 1, 2, ... and a
 *   middle or last fragment arriving before any first fragment is dropped as FRAGMENT_SEQUENCE.
 * - Incomplete frames stay buffered. Call [reset] when the link restarts or a request times out.
 */
class FrameReassembler(private val maxFrameSize: Int = DEFAULT_MAX_FRAME) {
    private var buffer = ByteArray(0)
    private val fragments = mutableListOf<ByteArray>()
    private var fragmentsStarted = false

    val pendingBytes: Int get() = buffer.size

    fun feed(chunk: ByteArray): List<RxEvent> {
        buffer += chunk
        val events = mutableListOf<RxEvent>()
        while (buffer.isNotEmpty()) {
            if (buffer[0].u8() != LinkFrame.MAGIC) {
                events += skipToMagic()
                continue
            }
            if (buffer.size < 3) break
            val total = ((buffer[1].u8() shl 8) or buffer[2].u8()) + 5
            if (total < MIN_FRAME || total > maxFrameSize) {
                events += RxEvent.Dropped(DropReason.BAD_LENGTH, consume(1))
                continue
            }
            if (buffer.size < total) break
            val frame = buffer.copyOfRange(0, total)
            if (Crc16.xmodem(frame) != 0) {
                events += RxEvent.Dropped(DropReason.BAD_CRC, consume(1))
                continue
            }
            consume(total)
            handleFrame(frame)?.let { events += it }
        }
        return events
    }

    fun reset() {
        buffer = ByteArray(0)
        fragments.clear()
        fragmentsStarted = false
    }

    private fun handleFrame(frame: ByteArray): RxEvent? {
        val end = frame.size - 2
        val flag = frame[3].u8()
        if (flag == LinkFrame.FLAG_SINGLE) return RxEvent.Payload(frame.copyOfRange(4, end))
        if (flag !in FLAG_FIRST..FLAG_LAST) return RxEvent.Dropped(DropReason.UNKNOWN_FLAG, frame)
        if (frame.size < MIN_FRAGMENT) return RxEvent.Dropped(DropReason.BAD_LENGTH, frame)
        if (flag == FLAG_FIRST) {
            fragments.clear()
            fragmentsStarted = true
        }
        if (!fragmentsStarted || frame[4].u8() != fragments.size) {
            fragments.clear()
            fragmentsStarted = false
            return RxEvent.Dropped(DropReason.FRAGMENT_SEQUENCE, frame)
        }
        fragments += frame.copyOfRange(5, end)
        if (flag != FLAG_LAST) return null
        val message = fragments.fold(ByteArray(0)) { acc, part -> acc + part }
        fragments.clear()
        fragmentsStarted = false
        return RxEvent.Payload(message)
    }

    private fun skipToMagic(): RxEvent {
        val reason = if (buffer[0].u8() in FOREIGN_LEADERS) DropReason.FOREIGN_DIALECT else DropReason.JUNK
        val next = buffer.indexOfFirst { it.u8() == LinkFrame.MAGIC }
        return RxEvent.Dropped(reason, consume(if (next < 0) buffer.size else next))
    }

    private fun consume(count: Int): ByteArray {
        val head = buffer.copyOfRange(0, count)
        buffer = buffer.copyOfRange(count, buffer.size)
        return head
    }

    companion object {
        const val DEFAULT_MAX_FRAME = 4096
        private const val MIN_FRAME = 6
        private const val MIN_FRAGMENT = 7
        private const val FLAG_FIRST = 1
        private const val FLAG_LAST = 3
        private val FOREIGN_LEADERS = setOf(0x7F, 0x89, 0x8A)
    }
}
