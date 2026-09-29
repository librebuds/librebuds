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
import org.junit.jupiter.api.Assertions.assertThrows
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
        // Wire bytes 11 22 33 44 55 61 are least-significant first, so the address reads back reversed.
        assertEquals("61:55:44:33:22:11", host.mac)
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
    fun rowWithoutCountIsIgnored() {
        val packet = Packet(CommandId(0x2B, 0x31), listOf(Tlv.of(3, 0), Tlv(4, "11 22 33 44 55 61".hexToBytes())))
        assertNull(Multipoint.parseRow(packet))
    }

    @Test
    fun rowWithoutIndexIsIgnored() {
        val packet = Packet(CommandId(0x2B, 0x31), listOf(Tlv.of(2, 1), Tlv(4, "11 22 33 44 55 61".hexToBytes())))
        assertNull(Multipoint.parseRow(packet))
    }

    @Test
    fun buildsRequests() {
        // Addresses go out least-significant byte first, so 11:22:…:66 is sent reversed.
        assertEquals("2B 31 01 00", Multipoint.enumerate().toPayload().toHex())
        assertEquals("2B 32 01 06 66 55 44 33 22 11", Multipoint.setPreferred("11:22:33:44:55:66").toPayload().toHex())
        assertEquals("2B 33 02 06 66 55 44 33 22 11", Multipoint.execute(HostAction.DISCONNECT, "11:22:33:44:55:66").toPayload().toHex())
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

    @Test
    fun collectorStartsANewBurstAfterACompleteList() {
        val collector = HostCollector()
        assertNull(collector.add(Multipoint.parseRow(row(index = 0, count = 3))!!))
        assertNull(collector.add(Multipoint.parseRow(row(index = 1, count = 3))!!))
        assertEquals(listOf(0, 1, 2), collector.add(Multipoint.parseRow(row(index = 2, count = 3))!!)!!.map { it.index })
        // The next burst reports one host fewer: exactly its two rows, nothing left from the first.
        assertNull(collector.add(Multipoint.parseRow(row(index = 1, count = 2))!!))
        val second = collector.add(Multipoint.parseRow(row(index = 0, count = 2))!!)!!
        assertEquals(listOf(0, 1), second.map { it.index })
        assertEquals(listOf(2, 2), second.map { it.count })
    }

    @Test
    fun collectorDropsACutShortBurstWhenTheCountChanges() {
        val collector = HostCollector()
        assertNull(collector.add(Multipoint.parseRow(row(index = 0, count = 3))!!))
        assertNull(collector.add(Multipoint.parseRow(row(index = 1, count = 3))!!))
        val next = collector.add(Multipoint.parseRow(row(index = 0, count = 1))!!)!!
        assertEquals(listOf(1), next.map { it.count })
    }

    @Test
    fun collectorIgnoresRowsOutsideTheirCount() {
        val collector = HostCollector()
        assertNull(collector.add(Multipoint.parseRow(row(index = 0, count = 0))!!))
        assertNull(collector.add(Multipoint.parseRow(row(index = 2, count = 2))!!))
        assertNull(collector.add(Multipoint.parseRow(row(index = 0, count = -1))!!))
        assertEquals(emptyList<HostRow>(), collector.partial())
        assertEquals(listOf(0), collector.add(Multipoint.parseRow(row(index = 0, count = 1))!!)!!.map { it.index })
    }

    @Test
    fun addressesAreLeastSignificantByteFirst() {
        // Round-2 capture: this row is the test phone at 11:22:33:44:55:66.
        val captured = Packet(
            CommandId(0x2B, 0x31),
            listOf(Tlv.of(2, 3), Tlv.of(3, 1), Tlv(4, "66 55 44 33 22 11".hexToBytes()), Tlv.of(5, 1)),
        )
        assertEquals("11:22:33:44:55:66", Multipoint.parseRow(captured)!!.mac)

        // Outgoing commands reverse again; the device rejects the other order.
        val request = Multipoint.execute(HostAction.DISCONNECT, "11:22:33:44:55:66")
        assertEquals("66 55 44 33 22 11", request.find(HostAction.DISCONNECT.code)!!.toHex())
        assertEquals("66 55 44 33 22 11", Multipoint.setPreferred("11:22:33:44:55:66").find(1)!!.toHex())
    }

    @Test
    fun malformedMacIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Multipoint.execute(HostAction.CONNECT, "ZZ:22:33:44:55:66") }
        assertThrows(IllegalArgumentException::class.java) { Multipoint.execute(HostAction.CONNECT, "11:22:33:44:55") }
        assertThrows(IllegalArgumentException::class.java) { Multipoint.setPreferred("112233445566") }
    }
}
