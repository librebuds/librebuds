// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.bt.LinkFactory
import io.github.librebuds.diag.FrameLog
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.command.Anc
import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.Battery
import io.github.librebuds.protocol.command.DeviceInfoCommand
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.state.BudsRepository
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.DeviceSummary
import io.github.librebuds.state.LinkError
import io.github.librebuds.state.LinkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

class AncRejectedException : IllegalStateException("The earbuds did not apply the noise-control mode")

class NotConnectedException : IllegalStateException("Earbuds not connected")

/**
 * Bluetooth-backed repository: one session at a time, state updated from replies and reports.
 *
 * The controller is confined to the main thread: [scope] is the app's main scope, and callers
 * ([connect], [setAnc], [refresh], [takeOver], [disconnect]) run there too (service, widget
 * and view models all use main-thread scopes). The `@Volatile` fields and the [AtomicInteger]
 * [generation] are a cheap safety margin, not a license to call in from other threads.
 * [connectLock] serializes [connect] calls against each other, but [disconnect] deliberately
 * does not take it, so it can win over an in-progress [connect]: every suspension point inside
 * [connect] re-checks [isCurrent] (generation, session identity, and whether the session already
 * closed) before publishing state, and bails without touching state otherwise, leaving whatever [disconnect] or [onClosed] already set.
 */
class BudsController(
    private val linkFactory: LinkFactory,
    private val registry: ProfileRegistry,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val isAudioConnected: (String) -> Boolean = { false },
    private val settleMillis: Long = 1500,
    val frameLog: FrameLog? = null,
) : BudsRepository {
    private val mutable = MutableStateFlow(BudsState())
    private val connectLock = Mutex()

    // Bumped by every connect() attempt and by disconnect(), so a connect() that resumes after a
    // disconnect() (or a newer connect()) recognizes it is stale and stops touching state.
    private val generation = AtomicInteger(0)

    @Volatile
    private var session: DeviceSession? = null

    @Volatile
    private var profile: Profile = ProfileRegistry.GENERIC

    // The packets collector for the current session; SharedFlow.collect() never completes on its
    // own, so this must be cancelled explicitly whenever the session it was collecting for ends.
    @Volatile
    private var collectorJob: Job? = null

    override val state: StateFlow<BudsState> = mutable.asStateFlow()

    suspend fun connect(address: String, name: String?) = connectLock.withLock {
        if (session != null && mutable.value.address == address && mutable.value.isConnected) return@withLock
        val myGeneration = generation.incrementAndGet()
        closeSession()
        mutable.value = BudsState(link = LinkState.CONNECTING, address = address, name = name)
        val link = try {
            linkFactory.open(address)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation.get() == myGeneration) mutable.update { it.copy(link = LinkState.DISCONNECTED) }
            return@withLock
        }
        if (generation.get() != myGeneration) {
            runCatching { link.close() }
            return@withLock
        }
        val current = DeviceSession(link, scope, onFrame = { direction, bytes -> frameLog?.record(direction, bytes) })
        session = current
        val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) { current.packets.collect(::applyPacket) }
        collectorJob = collector
        scope.launch { onClosed(current, address, collector, current.closed.await()) }

        val info = current.request(DeviceInfoCommand.request()).getOrNull()?.let(DeviceInfoCommand::parse)
        if (!isCurrent(current, myGeneration)) return@withLock
        profile = registry.match(sku = info?.sku, btName = name)
        mutable.update {
            it.copy(
                link = LinkState.CONNECTED,
                profileId = profile.id,
                capabilities = profile.capabilities.keys,
                device = DeviceSummary(model = profile.name, firmware = info?.firmware, serial = info?.serial),
                updatedAtMillis = clock(),
            )
        }

        val batteryResult = current.request(Battery.request())
        if (!isCurrent(current, myGeneration)) return@withLock
        batteryResult.onSuccess(::applyPacket)

        if (profile.supports("anc")) {
            val ancResult = current.request(Anc.readRequest())
            if (!isCurrent(current, myGeneration)) return@withLock
            ancResult.onSuccess(::applyPacket)
        }
    }

    fun disconnect() {
        generation.incrementAndGet()
        closeSession()
        mutable.update { it.copy(link = LinkState.DISCONNECTED) }
    }

    override suspend fun setAnc(mode: AncMode): Result<AncState> {
        val current = session ?: return Result.failure(NotConnectedException())
        try {
            current.send(Anc.writeRequest(mode, levelFor(mode, mutable.value.anc, profile)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(SessionClosedException())
        }
        delay(settleMillis)
        val reply = current.request(Anc.readRequest()).getOrElse { return Result.failure(it) }
        applyPacket(reply)
        val applied = Anc.parseState(reply) ?: return Result.failure(AncRejectedException())
        return if (Anc.confirms(applied, mode)) Result.success(applied) else Result.failure(AncRejectedException())
    }

    override suspend fun refresh(): Result<Unit> {
        val current = session ?: return Result.failure(NotConnectedException())
        current.request(Battery.request()).onSuccess(::applyPacket)
        if (profile.supports("anc")) current.request(Anc.readRequest()).onSuccess(::applyPacket)
        return Result.success(Unit)
    }

    override suspend fun takeOver(): Result<Unit> {
        val address = mutable.value.address ?: return Result.failure(NotConnectedException())
        connect(address, mutable.value.name)
        return if (mutable.value.isConnected) Result.success(Unit) else Result.failure(NotConnectedException())
    }

    private fun applyPacket(packet: Packet) {
        Battery.parse(packet)?.let { battery -> mutable.update { it.copy(battery = battery, updatedAtMillis = clock()) } }
        Anc.parseState(packet)?.let { anc -> mutable.update { it.copy(anc = anc, updatedAtMillis = clock()) } }
    }

    /** True while [current] is still the session [connect] should be allowed to publish state for. */
    private fun isCurrent(current: DeviceSession, myGeneration: Int): Boolean =
        generation.get() == myGeneration && session === current && !current.closed.isCompleted

    private fun onClosed(closed: DeviceSession, address: String, collector: Job, reason: Throwable?) {
        // Cancel this session's collector regardless of whether it is still the current one: a
        // newer connect() may already have replaced [session], but this collector belongs to
        // [closed] and nothing else will ever stop it.
        collector.cancel()
        if (session !== closed) return
        session = null
        // A session that gave up on unanswered requests closed itself: the earbuds went silent, no
        // other device took them. Only a drop from the remote side while audio is up is TAKEN_OVER.
        if (reason is SessionGaveUpException) {
            mutable.update { it.copy(link = LinkState.DISCONNECTED, lastError = LinkError.NO_REPLY) }
            return
        }
        val next = if (isAudioConnected(address)) LinkState.TAKEN_OVER else LinkState.DISCONNECTED
        mutable.update { it.copy(link = next) }
    }

    private fun closeSession() {
        val old = session
        session = null
        collectorJob?.cancel()
        collectorJob = null
        old?.close()
    }
}
