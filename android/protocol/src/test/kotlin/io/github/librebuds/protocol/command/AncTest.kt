package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.packetOf
import io.github.librebuds.protocol.util.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AncTest {
    @Test
    fun buildsReadRequest() {
        assertEquals("5A 00 05 00 2B 2A 01 00 42 7E", Anc.readRequest().toFrame().toHex())
    }

    @Test
    fun buildsWriteRequestsInModeLevelOrder() {
        assertEquals("5A 00 07 00 2B 04 01 02 02 00 B4 4F", Anc.writeRequest(AncMode.AWARENESS, 0).toFrame().toHex())
        assertEquals("5A 00 07 00 2B 04 01 02 01 03 D1 7F", Anc.writeRequest(AncMode.CANCELLATION, 3).toFrame().toHex())
        assertEquals("5A 00 07 00 2B 04 01 02 00 03 E2 4E", Anc.writeRequest(AncMode.OFF, 3).toFrame().toHex())
    }

    @Test
    fun readsStateInLevelModeOrder() {
        assertEquals(AncState(modeCode = 0, level = 3), Anc.parseState(packetOf("5A 00 07 00 2B 2A 01 02 03 00 40 62")))
        val awareness = Anc.parseState(packetOf("5A 00 07 00 2B 2A 01 02 02 02 53 11"))!!
        assertEquals(AncMode.AWARENESS, awareness.mode)
        assertEquals(2, awareness.level)
    }

    @Test
    fun unknownModeCodeIsKeptButUnnamed() {
        val state = AncState(modeCode = 7, level = 0)
        assertNull(state.mode)
    }

    @Test
    fun acknowledgesWrite() {
        assertEquals(true, Anc.isWriteAccepted(packetOf("5A 00 06 00 2B 04 02 01 00 31 71")))
        assertNull(Anc.isWriteAccepted(packetOf("5A 00 07 00 2B 2A 01 02 00 00 15 31")))
    }

    @Test
    fun confirmationComparesModeOnly() {
        assertTrue(Anc.confirms(AncState(modeCode = 2, level = 2), AncMode.AWARENESS))
        assertFalse(Anc.confirms(AncState(modeCode = 0, level = 3), AncMode.CANCELLATION))
    }

    @Test
    fun levelConfirmationNeedsCancellationAndTheLevel() {
        assertTrue(Anc.confirmsLevel(AncState(modeCode = 1, level = 1), 1))
        assertFalse(Anc.confirmsLevel(AncState(modeCode = 1, level = 3), 1))
        assertFalse(Anc.confirmsLevel(AncState(modeCode = 2, level = 1), 1))
        assertFalse(Anc.confirmsLevel(AncState(modeCode = 0, level = 1), 1))
    }

    @Test
    fun rejectsLevelOutsideByteRange() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            Anc.writeRequest(AncMode.CANCELLATION, 256)
        }
    }
}
