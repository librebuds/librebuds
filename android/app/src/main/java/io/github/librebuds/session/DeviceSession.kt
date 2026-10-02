// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.bt.Link
import io.github.librebuds.diag.FrameDirection
import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.frame.FrameReassembler
import io.github.librebuds.protocol.frame.RxEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class SessionClosedException : IOException("link closed")

/** Completes [DeviceSession.closed] when the session closed itself after too many unanswered requests. */
class SessionGaveUpException : IOException("no reply from the earbuds")

class RequestTimeoutException(val id: CommandId) : IOException("no reply to $id")

/**
 * Request/response over one link. One request is in flight at a time; replies are matched by
 * service/command, never by arrival order. Every decoded packet (replies included) is also
 * emitted on [packets] so unsolicited reports and replies update state the same way.
 *
 * [closed] completes with null for [close] and a clean end of stream, with
 * [SessionGaveUpException] after [maxConsecutiveFailures] unanswered requests in a row, or with
 * the reader's error otherwise.
 */
class DeviceSession(
    private val link: Link,
    scope: CoroutineScope,
    private val timeoutMillis: Long = 3000,
    private val retries: Int = 2,
    private val maxConsecutiveFailures: Int = 3,
    private val onFrame: (FrameDirection, ByteArray) -> Unit = { _, _ -> },
) {
    private class Waiter(val reply: CommandId, val accept: (Packet) -> Boolean, val result: CompletableDeferred<Packet>)

    private val reassembler = FrameReassembler()
    private val resetRequested = AtomicBoolean(false)
    private val requestLock = Mutex()
    private val mutablePackets = MutableSharedFlow<Packet>(extraBufferCapacity = 64)
    private val closedSignal = CompletableDeferred<Throwable?>()

    @Volatile
    private var waiter: Waiter? = null
    private var consecutiveFailures = 0

    val packets: SharedFlow<Packet> = mutablePackets
    val closed: Deferred<Throwable?> = closedSignal

    init {
        scope.launch { readLoop() }
    }

    private suspend fun readLoop() {
        val buffer = ByteArray(1024)
        var failure: Throwable? = null
        try {
            while (true) {
                val count = link.read(buffer)
                if (count < 0) break
                if (count == 0) {
                    yield()
                    continue
                }
                val chunk = buffer.copyOf(count)
                onFrame(FrameDirection.RX, chunk)
                if (resetRequested.getAndSet(false)) reassembler.reset()
                for (event in reassembler.feed(chunk)) {
                    if (event !is RxEvent.Payload) continue
                    val packet = Packet.fromPayload(event.bytes) ?: continue
                    // Emit on the packets flow before completing a matching waiter: on the
                    // single-threaded scheduler our tests run on, this guarantees a packets
                    // collector observes the packet before the resumed request() call returns.
                    // It is not a guarantee across real threads/dispatchers.
                    mutablePackets.emit(packet)
                    waiter?.let { if (it.reply == packet.id && it.accept(packet)) it.result.complete(packet) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e
        } finally {
            // The reader is done - end of stream, an error, or the scope was cancelled - so the
            // socket is no longer being serviced. Release it here rather than only on an explicit
            // close() call.
            runCatching { link.close() }
            waiter?.result?.completeExceptionally(SessionClosedException())
            closedSignal.complete(failure)
        }
    }

    /**
     * Sends [packet] and waits for the reply with id [reply] that [accept] takes (commands that answer
     * many logical reads under one id, or push unsolicited frames with it, narrow the match this way;
     * every packet still reaches [packets]). [timeoutMillis] and [retries] default to the session's. With [countsTowardGiveUp] = false an unanswered request (for example an
     * optional, unverified setting read) neither adds to nor trips the give-up counter; any answered
     * request still resets it.
     */
    suspend fun request(
        packet: Packet,
        reply: CommandId = packet.id,
        timeoutMillis: Long = this.timeoutMillis,
        retries: Int = this.retries,
        countsTowardGiveUp: Boolean = true,
        accept: (Packet) -> Boolean = { true },
    ): Result<Packet> = requestLock.withLock {
        repeat(retries + 1) {
            if (closedSignal.isCompleted) return Result.failure(SessionClosedException())
            val current = Waiter(reply, accept, CompletableDeferred())
            waiter = current
            if (closedSignal.isCompleted) {
                waiter = null
                return Result.failure(SessionClosedException())
            }
            try {
                write(packet.toFrame())
                // await() throws SessionClosedException (an IOException, caught below) if the link ends.
                val answer = withTimeoutOrNull(timeoutMillis) { current.result.await() }
                if (answer != null) {
                    consecutiveFailures = 0
                    return Result.success(answer)
                }
                resetRequested.set(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return Result.failure(e)
            } finally {
                waiter = null
            }
        }
        if (countsTowardGiveUp) {
            consecutiveFailures++
            if (consecutiveFailures >= maxConsecutiveFailures) closeWith(SessionGaveUpException())
        }
        Result.failure(RequestTimeoutException(reply))
    }

    suspend fun send(packet: Packet) {
        requestLock.withLock { write(packet.toFrame()) }
    }

    fun close() = closeWith(null)

    private fun closeWith(reason: Throwable?) {
        // Complete closedSignal before closing the link: a requested close is a clean end (null), a
        // give-up is a [SessionGaveUpException], and completing it first wins the race against the
        // reader coroutine's own completion, which would otherwise report the reader's IOException
        // from the closed socket.
        closedSignal.complete(reason)
        waiter?.result?.completeExceptionally(SessionClosedException())
        runCatching { link.close() }
    }

    private suspend fun write(frame: ByteArray) {
        onFrame(FrameDirection.TX, frame)
        link.write(frame)
    }
}
