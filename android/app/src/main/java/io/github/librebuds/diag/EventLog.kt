// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.diag

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The app's own recent connection events (service, controller, companion, beacon scan) for the
 * diagnostics export. Addresses are masked on the way in, so the buffer never holds a full MAC.
 */
class EventLog(private val capacity: Int = 300, private val clock: () -> Long = System::currentTimeMillis) {
    private data class Entry(val ts: Long, val tag: String, val msg: String)

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun record(tag: String, msg: String) {
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(Entry(clock(), tag, maskMacs(msg)))
    }

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun toJsonl(): String = entries.joinToString("\n") {
        buildJsonObject {
            put("type", JsonPrimitive("event"))
            put("ts", JsonPrimitive(it.ts))
            put("tag", JsonPrimitive(it.tag))
            put("msg", JsonPrimitive(it.msg))
        }.toString()
    }

    companion object {
        private val MAC = Regex("\\b(?:[0-9A-Fa-f]{2}[:-]){4}([0-9A-Fa-f]{2})[:-]([0-9A-Fa-f]{2})\\b")

        /** Replaces every MAC address in [text] with one that keeps only its last two bytes. */
        fun maskMacs(text: String): String = MAC.replace(text) { "**:**:**:**:${it.groupValues[1]}:${it.groupValues[2]}" }

        fun maskMac(address: String?): String = address?.let(::maskMacs) ?: "none"
    }
}
