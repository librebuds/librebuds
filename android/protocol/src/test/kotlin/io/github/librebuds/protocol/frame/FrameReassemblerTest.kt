package io.github.librebuds.protocol.frame

import io.github.librebuds.protocol.util.hexToBytes
import io.github.librebuds.protocol.util.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrameReassemblerTest {
    private val batteryRequest = "5A 00 09 00 01 08 01 00 02 00 03 00 FB B9"
    private val ancState = "5A 00 07 00 2B 2A 01 02 00 00 15 31"
    private val batteryPush = "5A 00 10 00 01 27 01 01 5A 02 03 64 5A 30 03 03 00 00 01 E7 9E"

    private fun payloads(events: List<RxEvent>) = events.filterIsInstance<RxEvent.Payload>().map { it.bytes.toHex() }
    private fun drops(events: List<RxEvent>) = events.filterIsInstance<RxEvent.Dropped>().map { it.reason }

    @Test
    fun deliversSingleFramePayload() {
        val events = FrameReassembler().feed(batteryRequest.hexToBytes())
        assertEquals(listOf("01 08 01 00 02 00 03 00"), payloads(events))
        assertEquals(emptyList<DropReason>(), drops(events))
    }

    @Test
    fun waitsForFrameSplitAcrossChunks() {
        val r = FrameReassembler()
        val bytes = batteryRequest.hexToBytes()
        assertTrue(r.feed(bytes.copyOfRange(0, 2)).isEmpty())
        assertTrue(r.feed(bytes.copyOfRange(2, 9)).isEmpty())
        assertEquals(listOf("01 08 01 00 02 00 03 00"), payloads(r.feed(bytes.copyOfRange(9, bytes.size))))
        assertEquals(0, r.pendingBytes)
    }

    @Test
    fun splitsCoalescedFramesInOrder() {
        val events = FrameReassembler().feed("$ancState $batteryPush".hexToBytes())
        assertEquals(listOf("2B 2A 01 02 00 00", "01 27 01 01 5A 02 03 64 5A 30 03 03 00 00 01"), payloads(events))
    }

    @Test
    fun skipsJunkBeforeFrame() {
        val events = FrameReassembler().feed("00 11 $ancState".hexToBytes())
        assertEquals(listOf(DropReason.JUNK), drops(events))
        assertEquals(listOf("2B 2A 01 02 00 00"), payloads(events))
    }

    @Test
    fun reportsForeignDialectLeader() {
        val events = FrameReassembler().feed("7F 01 80 80 $ancState".hexToBytes())
        assertEquals(listOf(DropReason.FOREIGN_DIALECT), drops(events))
        assertEquals(1, payloads(events).size)
    }

    @Test
    fun recoversAfterBadCrc() {
        val corrupted = "5A 00 07 00 2B 2A 01 02 00 00 15 32"
        val events = FrameReassembler().feed("$corrupted $batteryRequest".hexToBytes())
        assertTrue(DropReason.BAD_CRC in drops(events))
        assertEquals(listOf("01 08 01 00 02 00 03 00"), payloads(events))
    }

    @Test
    fun rejectsImpossibleLengthAndResyncs() {
        val events = FrameReassembler().feed("5A FF FF $ancState".hexToBytes())
        assertEquals(DropReason.BAD_LENGTH, drops(events).first())
        assertEquals(listOf("2B 2A 01 02 00 00"), payloads(events))
    }

    @Test
    fun reassemblesFragments() {
        val first = "5A 00 05 01 00 01 08 01 1D F0"
        val middle = "5A 00 03 02 01 01 20 3A"
        val last = "5A 00 03 03 02 64 7E 5A"
        val r = FrameReassembler()
        assertTrue(r.feed(first.hexToBytes()).isEmpty())
        assertTrue(r.feed(middle.hexToBytes()).isEmpty())
        assertEquals(listOf("01 08 01 01 64"), payloads(r.feed(last.hexToBytes())))
    }

    @Test
    fun discardsMessageWithMissingFragment() {
        val first = "5A 00 05 01 00 01 08 01 1D F0"
        val lastWithIndexTwo = "5A 00 03 03 02 64 7E 5A"
        val events = FrameReassembler().feed("$first $lastWithIndexTwo".hexToBytes())
        assertEquals(listOf(DropReason.FRAGMENT_SEQUENCE), drops(events))
        assertEquals(emptyList<String>(), payloads(events))
    }

    @Test
    fun staleLengthIsRecoverableWithReset() {
        val r = FrameReassembler()
        // A stray 0x5A that looks like the start of a 261-byte frame.
        assertTrue(r.feed("5A 01 00 00".hexToBytes()).isEmpty())
        assertEquals(4, r.pendingBytes)
        r.reset()
        assertEquals(0, r.pendingBytes)
        assertEquals(listOf("2B 2A 01 02 00 00"), payloads(r.feed(ancState.hexToBytes())))
    }
}
