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
import io.github.librebuds.state.LinkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AncRejectedException : IllegalStateException("The earbuds did not apply the noise-control mode")

class NotConnectedException : IllegalStateException("Earbuds not connected")

/** Bluetooth-backed repository: one session at a time, state updated from replies and reports. */
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
    private var session: DeviceSession? = null
    private var profile: Profile = ProfileRegistry.GENERIC

    override val state: StateFlow<BudsState> = mutable.asStateFlow()

    suspend fun connect(address: String, name: String?) = connectLock.withLock {
        if (session != null && mutable.value.address == address && mutable.value.isConnected) return@withLock
        closeSession()
        mutable.value = BudsState(link = LinkState.CONNECTING, address = address, name = name)
        val link = try {
            linkFactory.open(address)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutable.update { it.copy(link = LinkState.DISCONNECTED) }
            return@withLock
        }
        val current = DeviceSession(link, scope, onFrame = { direction, bytes -> frameLog?.record(direction, bytes) })
        session = current
        scope.launch(start = CoroutineStart.UNDISPATCHED) { current.packets.collect(::applyPacket) }
        scope.launch { current.closed.await(); onClosed(current, address) }

        val info = current.request(DeviceInfoCommand.request()).getOrNull()?.let(DeviceInfoCommand::parse)
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
        current.request(Battery.request()).onSuccess(::applyPacket)
        if (profile.supports("anc")) current.request(Anc.readRequest()).onSuccess(::applyPacket)
    }

    fun disconnect() {
        closeSession()
        mutable.update { it.copy(link = LinkState.DISCONNECTED) }
    }

    override suspend fun setAnc(mode: AncMode): Result<AncState> {
        val current = session ?: return Result.failure(NotConnectedException())
        current.send(Anc.writeRequest(mode, levelFor(mode, mutable.value.anc, profile)))
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

    private fun onClosed(closed: DeviceSession, address: String) {
        if (session !== closed) return
        session = null
        val next = if (isAudioConnected(address)) LinkState.TAKEN_OVER else LinkState.DISCONNECTED
        mutable.update { it.copy(link = next) }
    }

    private fun closeSession() {
        val old = session
        session = null
        old?.close()
    }
}
