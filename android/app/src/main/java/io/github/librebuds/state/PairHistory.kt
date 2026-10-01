// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import android.content.Context
import androidx.core.content.edit

/**
 * When each pair of FreeBuds last connected to the phone, by uppercase address. Decides which pair the
 * app opens on and which one the service connects first when several are connected. The encoded form
 * is `ADDRESS=millis` entries separated by `;`; anything malformed is skipped.
 */
object PairHistoryCodec {
    /** Keeps the list small: more pairs than this are not realistic, the oldest go first. */
    const val MAX_ENTRIES = 16

    fun decode(text: String?): Map<String, Long> {
        if (text.isNullOrEmpty()) return emptyMap()
        return text.split(';').mapNotNull { entry ->
            val parts = entry.split('=', limit = 2)
            val millis = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            val address = parts[0].trim().uppercase().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            address to millis
        }.toMap()
    }

    fun encode(history: Map<String, Long>): String =
        history.entries.joinToString(";") { "${it.key}=${it.value}" }

    /** [history] with [address] set to [at], trimmed to the newest [MAX_ENTRIES]. */
    fun record(history: Map<String, Long>, address: String, at: Long): Map<String, Long> =
        (history + (address.uppercase() to at)).entries
            .sortedByDescending { it.value }
            .take(MAX_ENTRIES)
            .associate { it.key to it.value }
}

/** Persists [PairHistoryCodec] data in the `pair_history` preferences. */
class PairHistory(context: Context) {
    private val prefs = context.getSharedPreferences("pair_history", Context.MODE_PRIVATE)

    fun all(): Map<String, Long> = PairHistoryCodec.decode(prefs.getString(KEY, null))

    fun record(address: String, at: Long = System.currentTimeMillis()) = prefs.edit {
        putString(KEY, PairHistoryCodec.encode(PairHistoryCodec.record(all(), address, at)))
    }

    private companion object {
        const val KEY = "connected_at"
    }
}
