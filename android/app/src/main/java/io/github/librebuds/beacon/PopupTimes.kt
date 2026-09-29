// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** When the popup was last shown per cooldown key, stored as one small JSON object. */
object PopupTimes {
    /** Entries older than this (or stamped in the future) are dropped on every write. */
    const val MAX_AGE_MILLIS = 3_600_000L

    fun record(times: Map<String, Long>, key: String, at: Long): Map<String, Long> =
        times.filterValues { at - it in 0 until MAX_AGE_MILLIS } + (key to at)

    fun encode(times: Map<String, Long>): String = JsonObject(times.mapValues { JsonPrimitive(it.value) }).toString()

    fun decode(text: String?): Map<String, Long> {
        if (text == null) return emptyMap()
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyMap()
        return root.mapNotNull { (key, value) -> runCatching { value.jsonPrimitive.longOrNull }.getOrNull()?.let { key to it } }.toMap()
    }
}
