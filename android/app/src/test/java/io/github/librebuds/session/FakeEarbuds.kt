// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.frame.FrameReassembler
import io.github.librebuds.protocol.frame.RxEvent
import io.github.librebuds.protocol.tlv.Tlv

/**
 * Scripted earbuds speaking the confirmed subset: device info, battery, ANC read/write.
 * [acceptModes] lists mode codes the device applies; other writes are acknowledged and ignored.
 */
class FakeEarbuds(
    var sku: String = "BTFT0020",
    var levels: Triple<Int, Int, Int> = Triple(100, 90, 50),
    var ancMode: Int = 0,
    var ancLevel: Int = 3,
    val acceptModes: Set<Int> = setOf(0, 1, 2),
    var silent: Boolean = false,
) {
    private val reassembler = FrameReassembler()

    fun link(): FakeLink = FakeLink { bytes -> answer(bytes).forEach { deliver(it) } }

    fun ancPush(): ByteArray = Packet(CommandId(0x2B, 0x2A), listOf(Tlv.of(1, ancLevel, ancMode))).toFrame()

    private fun answer(bytes: ByteArray): List<ByteArray> {
        if (silent) return emptyList()
        return reassembler.feed(bytes).filterIsInstance<RxEvent.Payload>().mapNotNull { Packet.fromPayload(it.bytes) }.mapNotNull { request ->
            when (request.id.toString()) {
                "01/07" -> Packet(request.id, listOf(Tlv(15, sku.toByteArray()), Tlv(7, "FW 1.0.0.100".toByteArray()), Tlv(9, "TESTSERIAL000001".toByteArray())))
                "01/08" -> Packet(request.id, listOf(Tlv.of(1, minOf(levels.first, levels.second)), Tlv.of(2, levels.first, levels.second, levels.third), Tlv.of(3, 0, 0, 0)))
                "2B/2A" -> Packet(request.id, listOf(Tlv.of(1, ancLevel, ancMode)))
                "2B/04" -> {
                    val value = request.find(1)
                    if (value != null && value.size == 2 && (value[0].toInt() and 0xFF) in acceptModes) {
                        ancMode = value[0].toInt() and 0xFF
                        ancLevel = value[1].toInt() and 0xFF
                    }
                    Packet(request.id, listOf(Tlv.of(2, 0)))
                }
                else -> null
            }?.toFrame()
        }
    }
}
