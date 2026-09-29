// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.hexToBytes
import io.github.librebuds.protocol.util.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MultipointTest {
    private fun row(index: Int, count: Int = 2, state: Int = 1, preferred: Int? = null) = Packet(
        CommandId(0x2B, 0x31),
        listOfNotNull(
            Tlv.of(2, count), Tlv.of(3, index), Tlv(4, "11 22 33 44 55 6$index".hexToBytes()),
            Tlv(9, "Host $index".toByteArray()), Tlv.of(5, state), preferred?.let { Tlv.of(7, it) },
        ),
    )

    @Test
    fun parsesHostRow() {
        val host = Multipoint.parseRow(row(index = 1, state = 9, preferred = 1))!!
        assertEquals("11:22:33:44:55:61", host.mac)
        assertEquals("Host 1", host.name)
        assertTrue(host.connected)
        assertTrue(host.playing)
        assertTrue(host.preferred)
        assertNull(host.autoConnect)
    }

    @Test
    fun rowWithoutMacIsIgnored() {
        assertNull(Multipoint.parseRow(Packet(CommandId(0x2B, 0x31), listOf(Tlv.of(2, 1), Tlv.of(3, 0)))))
    }

    @Test
    fun buildsRequests() {
        assertEquals("2B 31 01 00", Multipoint.enumerate().toPayload().toHex())
        assertEquals("2B 32 01 06 11 22 33 44 55 66", Multipoint.setPreferred("11:22:33:44:55:66").toPayload().toHex())
        assertEquals("2B 33 02 06 11 22 33 44 55 66", Multipoint.execute(HostAction.DISCONNECT, "11:22:33:44:55:66").toPayload().toHex())
        assertEquals("2B 2E 01 01 00", Multipoint.writeToggle(false).toPayload().toHex())
    }

    @Test
    fun noUnpairAction() {
        assertFalse(HostAction.entries.any { it.code == 3 })
    }

    @Test
    fun collectorHandlesDuplicatesAndOrder() {
        val collector = HostCollector()
        assertNull(collector.add(Multipoint.parseRow(row(index = 1))!!))
        assertNull(collector.add(Multipoint.parseRow(row(index = 1))!!))
        val all = collector.add(Multipoint.parseRow(row(index = 0))!!)!!
        assertEquals(listOf(0, 1), all.map { it.index })
    }
}
