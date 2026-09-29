// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.bt.Link
import kotlinx.coroutines.channels.Channel

/** In-memory link. [respond] runs for every write and may call [deliver] to answer. */
class FakeLink(private val respond: suspend FakeLink.(ByteArray) -> Unit = {}) : Link {
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    val written = mutableListOf<ByteArray>()
    var closed = false
        private set

    override suspend fun read(buffer: ByteArray): Int {
        val chunk = incoming.receiveCatching().getOrNull() ?: return -1
        chunk.copyInto(buffer)
        return chunk.size
    }

    override suspend fun write(bytes: ByteArray) {
        check(!closed) { "link closed" }
        written += bytes
        respond(bytes)
    }

    suspend fun deliver(bytes: ByteArray) = incoming.send(bytes)

    fun endOfStream() {
        incoming.close()
    }

    override fun close() {
        closed = true
        incoming.close()
    }
}
