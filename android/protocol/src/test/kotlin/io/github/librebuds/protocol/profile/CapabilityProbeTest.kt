// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.profile

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.command.Status
import io.github.librebuds.protocol.tlv.Tlv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CapabilityProbeTest {
    private fun status(code: Int) = Packet(
        CommandId(0x2B, 0x30),
        listOf(Tlv(Status.TYPE, byteArrayOf(0, (code shr 16).toByte(), (code shr 8).toByte(), code.toByte()))),
    )

    private val data = Packet(CommandId(0x2B, 0x11), listOf(Tlv.of(1, 1)))

    @Test
    fun dataMeansSupported() {
        assertEquals(Support.SUPPORTED, CapabilityProbe.classify(data))
    }

    @Test
    fun explicitRefusalIsNotSupport() {
        assertEquals(Support.UNSUPPORTED, CapabilityProbe.classify(status(Status.UNSUPPORTED)))
    }

    @Test
    fun silenceIsItsOwnOutcome() {
        assertEquals(Support.SILENT, CapabilityProbe.classify(null))
    }

    @Test
    fun readsStatusCodes() {
        assertEquals(Status.UNSUPPORTED, Status.of(status(Status.UNSUPPORTED)))
        assertTrue(Status.isSuccess(status(Status.SUCCESS)))
        assertFalse(Status.isSuccess(status(Status.UNSUPPORTED)))
        assertNull(Status.of(data), "a reply without a status record has no code")
    }

    @Test
    fun foldsRepliesIntoCapabilityNames() {
        val replies = mapOf(
            CommandId(0x2B, 0x11) to data,
            CommandId(0x2B, 0x4A) to status(Status.UNSUPPORTED),
            CommandId(0x2B, 0x2F) to null,
        )
        val capabilities = CapabilityProbe.capabilities(replies)
        assertTrue("wear" in capabilities)
        assertFalse("equalizer" in capabilities, "an explicit refusal must not count as support")
        assertFalse("multipoint" in capabilities, "silence must not count as support")
    }

    @Test
    fun everyProbeAsksADistinctCommand() {
        val ids = CapabilityProbe.PROBES.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "probes must not ask the same command twice")
    }

    @Test
    fun probeNamesMatchProfileCapabilityKeys() {
        // Guards against probe names drifting away from the vocabulary used in profiles/*.json.
        val known = setOf(
            "battery", "wear", "equalizer", "gestures",
            "multipoint", "lowLatency", "soundQuality", "language",
        )
        for (probe in CapabilityProbe.PROBES) {
            assertTrue(probe.capability in known, "unknown capability name: ${probe.capability}")
        }
    }
}
