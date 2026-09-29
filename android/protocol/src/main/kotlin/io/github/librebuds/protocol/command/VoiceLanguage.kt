// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet

/** [current] voice-prompt language tag, and the [supported] list reported by the device. */
data class LanguageInfo(val current: String?, val supported: List<String>)

/**
 * Voice prompt language, read-only: 0C/02 (TLV 1 = current language tag, TLV 3 = comma-separated
 * list of supported tags). There is no write command; 0C/01 must never be sent (global constraints).
 */
object VoiceLanguage {
    val GET = CommandId(0x0C, 0x02)

    fun read(): Packet = Packet.read(GET, 1, 2, 3)

    fun parse(packet: Packet): LanguageInfo? {
        if (packet.id != GET) return null
        val current = packet.find(1)?.let { text(it) }
        val supported = packet.find(3)?.let { text(it) }
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        return LanguageInfo(current = current, supported = supported)
    }

    private fun text(value: ByteArray): String? =
        value.toString(Charsets.UTF_8).trimEnd('\u0000').trim().takeIf { it.isNotEmpty() }
}
