// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.frame.FrameReassembler
import io.github.librebuds.protocol.frame.RxEvent
import io.github.librebuds.protocol.tlv.Tlv

/** One gesture's assignment on the fake: action codes (-1 = off) and the supported code list. */
data class FakeGesture(var left: Int, var right: Int, var inCall: Int = -1, val supported: List<Int> = listOf(-1, 0, 1, 2))

/** One paired host on the fake; [state] uses the device's connection code (0 = not connected, 9 = playing). */
data class FakeHost(val mac: String, val name: String, var state: Int, var preferred: Boolean = false, var autoConnect: Boolean = true)

/**
 * Scripted earbuds: device info, battery, ANC read/write, and the per-model settings and
 * multipoint commands (synthetic, see the M4 global constraints).
 * [acceptModes] lists mode codes the device applies; other ANC writes are acknowledged and ignored.
 * [ignoreWrites] lists command ids (for example `"01/1F"`) the device acknowledges but does not apply.
 * [hosts] are answered one `2B/31` row per host, last index first so the controller has to sort;
 * [hostRowLimit] caps how many of those rows are sent; [reportAutoConnect] = false leaves TLV 8 out of them.
 * [ignoreReads] lists command ids the device never answers (the request is still recorded).
 * [lowLatencyAckLive] picks the low-latency write ack: the live value on TLV 2 (true) or a TLV 2 = 0
 * status (false), since which one the real device sends is unknown.
 * [onRequest] sees every decoded request id, in order.
 */
class FakeEarbuds(
    var sku: String = "BTFT0020",
    var levels: Triple<Int, Int, Int> = Triple(100, 90, 50),
    var ancMode: Int = 0,
    var ancLevel: Int = 3,
    val acceptModes: Set<Int> = setOf(0, 1, 2),
    var silent: Boolean = false,
    var wear: Boolean = true,
    val gestures: MutableMap<Gesture, FakeGesture> = Gesture.entries.associateWith { FakeGesture(left = 1, right = 1) }.toMutableMap(),
    var equalizerPreset: Int = 1,
    val equalizerPresets: List<Int> = listOf(1, 2, 3),
    var lowLatency: Boolean = false,
    var soundQuality: Int = 0,
    var language: String = "en-GB",
    val languages: List<String> = listOf("en-GB", "de-DE"),
    var multipoint: Boolean = true,
    val hosts: MutableList<FakeHost> = mutableListOf(),
    var hostRowLimit: Int = Int.MAX_VALUE,
    val ignoreWrites: Set<String> = emptySet(),
    val ignoreReads: Set<String> = emptySet(),
    var lowLatencyAckLive: Boolean = true,
    var reportAutoConnect: Boolean = true,
    var onRequest: (String) -> Unit = {},
) {
    private val reassembler = FrameReassembler()

    fun link(): FakeLink = FakeLink { bytes -> answer(bytes).forEach { deliver(it) } }

    fun ancPush(): ByteArray = Packet(CommandId(0x2B, 0x2A), listOf(Tlv.of(1, ancLevel, ancMode))).toFrame()

    fun inEarPush(inEar: Boolean): ByteArray = Packet(CommandId(0x2B, 0x03), listOf(Tlv.of(8, if (inEar) 1 else 0))).toFrame()

    /** The current [hosts] as unsolicited `2B/31` rows, in the same order an enumerate gets them. */
    fun hostRowPush(): List<ByteArray> = hostRows().map { it.toFrame() }

    fun hostChangePush(): ByteArray = Packet(CommandId(0x2B, 0x36), listOf(Tlv.of(1, 1))).toFrame()

    private fun answer(bytes: ByteArray): List<ByteArray> {
        if (silent) return emptyList()
        return reassembler.feed(bytes).filterIsInstance<RxEvent.Payload>().mapNotNull { Packet.fromPayload(it.bytes) }.flatMap { request ->
            val id = request.id.toString()
            onRequest(id)
            if (id in ignoreReads) return@flatMap emptyList()
            val apply = id !in ignoreWrites
            answerOne(request, id, apply).map { it.toFrame() }
        }
    }

    private fun answerOne(request: Packet, id: String, apply: Boolean): List<Packet> {
        Gesture.entries.firstOrNull { it.get == request.id }?.let { g ->
            val s = gestures.getValue(g)
            val tlvs = mutableListOf(Tlv.of(1, s.left), Tlv.of(2, s.right), Tlv.of(3, *s.supported.toIntArray()))
            if (request.find(4) != null) tlvs += Tlv.of(4, s.inCall)
            return listOf(Packet(request.id, tlvs))
        }
        Gesture.entries.firstOrNull { it.set == request.id }?.let { g ->
            if (apply) {
                val s = gestures.getValue(g)
                request.byte(1)?.let { s.left = it }
                request.byte(2)?.let { s.right = it }
                request.byte(4)?.let { s.inCall = it }
            }
            return listOf(ack(request))
        }
        return when (id) {
            "01/07" -> listOf(Packet(request.id, listOf(Tlv(15, sku.toByteArray()), Tlv(7, "FW 1.0.0.100".toByteArray()), Tlv(9, "TESTSERIAL000001".toByteArray()))))
            "01/08" -> listOf(Packet(request.id, listOf(Tlv.of(1, minOf(levels.first, levels.second)), Tlv.of(2, levels.first, levels.second, levels.third), Tlv.of(3, 0, 0, 0))))
            "2B/2A" -> listOf(Packet(request.id, listOf(Tlv.of(1, ancLevel, ancMode))))
            "2B/04" -> {
                val value = request.find(1)
                if (apply && value != null && value.size == 2 && (value[0].toInt() and 0xFF) in acceptModes) {
                    ancMode = value[0].toInt() and 0xFF
                    ancLevel = value[1].toInt() and 0xFF
                }
                listOf(Packet(request.id, listOf(Tlv.of(2, 0))))
            }
            "2B/11" -> listOf(Packet(request.id, listOf(Tlv.of(1, if (wear) 1 else 0))))
            "2B/10" -> {
                if (apply) request.byte(1)?.let { wear = it != 0 }
                listOf(ack(request))
            }
            "2B/4A" -> listOf(Packet(request.id, listOf(Tlv.of(2, equalizerPreset), Tlv.of(3, *equalizerPresets.toIntArray()))))
            "2B/49" -> {
                if (apply) request.byte(1)?.let { equalizerPreset = it }
                listOf(ack(request))
            }
            "2B/6C" -> {
                // One command both ways: a write carries a value on TLV 1, a read only asks for TLV 2.
                val write = request.byte(1)
                if (apply) write?.let { lowLatency = it != 0 }
                val value = if (write != null && !lowLatencyAckLive) 0 else if (lowLatency) 1 else 0
                listOf(Packet(request.id, listOf(Tlv.of(2, value))))
            }
            "2B/A3" -> listOf(Packet(request.id, listOf(Tlv.of(2, soundQuality))))
            "2B/A2" -> {
                if (apply) request.byte(1)?.let { soundQuality = it }
                listOf(ack(request))
            }
            "0C/02" -> listOf(Packet(request.id, listOf(Tlv(1, language.toByteArray()), Tlv(3, languages.joinToString(",").toByteArray()))))
            "2B/2F" -> listOf(Packet(request.id, listOf(Tlv.of(1, if (multipoint) 1 else 0))))
            "2B/2E" -> {
                if (apply) request.byte(1)?.let { multipoint = it != 0 }
                listOf(ack(request))
            }
            "2B/31" -> hostRows()
            "2B/32" -> {
                val mac = request.find(1)?.toMac()
                if (apply && mac != null) hosts.forEach { it.preferred = it.mac == mac }
                emptyList()
            }
            "2B/33" -> {
                val tlv = request.tlvs.firstOrNull()
                val host = tlv?.value?.toMac()?.let { mac -> hosts.firstOrNull { it.mac == mac } }
                if (apply && host != null) {
                    when (tlv.type) {
                        1 -> host.state = 1
                        2 -> host.state = 0
                        4 -> host.autoConnect = true
                        5 -> host.autoConnect = false
                    }
                }
                emptyList()
            }
            else -> emptyList()
        }
    }

    private fun hostRows(): List<Packet> = hosts.mapIndexed { index, host ->
        Packet(
            CommandId(0x2B, 0x31),
            listOf(
                Tlv.of(2, hosts.size),
                Tlv.of(3, index),
                Tlv(4, host.mac.split(":").map { it.toInt(16).toByte() }.toByteArray().reversedArray()),
                Tlv(9, host.name.toByteArray()),
                Tlv.of(5, host.state),
                Tlv.of(7, if (host.preferred) 1 else 0),
            ) + if (reportAutoConnect) listOf(Tlv.of(8, if (host.autoConnect) 1 else 0)) else emptyList(),
        )
    }.take(hostRowLimit).reversed()

    private fun ack(request: Packet) = Packet(request.id, listOf(Tlv.of(2, 0)))

    private fun Packet.byte(type: Int): Int? = find(type)?.takeIf { it.size == 1 }?.get(0)?.toInt()

    // Addresses travel least-significant byte first (see Multipoint), so they are reversed on the wire.
    private fun ByteArray.toMac(): String = reversedArray().joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
}
