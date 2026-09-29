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
import io.github.librebuds.protocol.command.Equalizer
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.command.GestureSetting
import io.github.librebuds.protocol.command.Gestures
import io.github.librebuds.protocol.command.HostAction
import io.github.librebuds.protocol.command.HostCollector
import io.github.librebuds.protocol.command.HostRow
import io.github.librebuds.protocol.command.LowLatency
import io.github.librebuds.protocol.command.Multipoint
import io.github.librebuds.protocol.command.SoundQuality
import io.github.librebuds.protocol.command.VoiceLanguage
import io.github.librebuds.protocol.command.WearDetection
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.state.BudsRepository
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.DeviceSettings
import io.github.librebuds.state.DeviceSummary
import io.github.librebuds.state.LinkError
import io.github.librebuds.state.LinkState
import io.github.librebuds.state.SettingChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.util.concurrent.atomic.AtomicInteger

/** The device acknowledged a write but a read-back shows it did not apply it (noise control or any setting). */
class AncRejectedException : IllegalStateException("The earbuds did not apply the change")

class NotConnectedException : IllegalStateException("Earbuds not connected")

/**
 * Bluetooth-backed repository: one session at a time, state updated from replies and reports.
 *
 * The controller is confined to the main thread: [scope] is the app's main scope, and callers
 * ([connect], [setAnc], [apply], [refreshHosts], [refresh], [takeOver], [disconnect]) run there too (service, widget
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
    private val hostListMillis: Long = 3000,
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

    // Accumulates 2B/31 rows; bumped [completedHostLists] each time a full list was published, so
    // [refreshHosts] can wait for the next one. Main-confined like the rest of the controller.
    private val hostCollector = HostCollector()
    private val completedHostLists = MutableStateFlow(0)

    // Refresh started by a 2B/36 change push; a newer push restarts it.
    private var hostPushJob: Job? = null

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

        readSettings(current, myGeneration)
    }

    /** Reads every setting the profile lists; stops (without touching state) once [current] is stale. */
    private suspend fun readSettings(current: DeviceSession, myGeneration: Int) {
        suspend fun read(packet: Packet): Boolean {
            val result = current.request(packet)
            if (!isCurrent(current, myGeneration)) return false
            result.onSuccess(::applyPacket)
            return true
        }
        if (profile.supports("wear") && !read(WearDetection.read())) return
        for ((gesture, withInCall) in profileGestures()) {
            if (!read(Gestures.read(gesture, withInCall))) return
        }
        if (profile.supports("equalizer") && !read(Equalizer.read())) return
        if (profile.supports("lowLatency") && !read(LowLatency.read())) return
        if (profile.supports("soundQuality") && !read(SoundQuality.read())) return
        if (profile.supports("language") && !read(VoiceLanguage.read())) return
        if (profile.supports("multipoint")) {
            if (!read(Multipoint.readToggle())) return
            refreshHosts()
        }
    }

    /** Gestures listed under the profile's `gestures` capability, each with whether to read its in-call action too. */
    private fun profileGestures(): List<Pair<Gesture, Boolean>> {
        val listed = profile.capabilities["gestures"] ?: return emptyList()
        return GESTURE_KEYS.mapNotNull { (key, gesture) ->
            val entry = listed[key] ?: return@mapNotNull null
            val inCall = ((entry as? JsonObject)?.get("inCall") as? JsonPrimitive)?.booleanOrNull == true
            gesture to inCall
        }
    }

    fun disconnect() {
        generation.incrementAndGet()
        closeSession()
        mutable.update { it.copy(link = LinkState.DISCONNECTED) }
    }

    override suspend fun setAnc(mode: AncMode): Result<AncState> {
        val current = session ?: return Result.failure(NotConnectedException())
        sendWrite(current, Anc.writeRequest(mode, levelFor(mode, mutable.value.anc, profile))).getOrElse { return Result.failure(it) }
        delay(settleMillis)
        val reply = current.request(Anc.readRequest()).getOrElse { return Result.failure(it) }
        applyPacket(reply)
        val applied = Anc.parseState(reply) ?: return Result.failure(AncRejectedException())
        return if (Anc.confirms(applied, mode)) Result.success(applied) else Result.failure(AncRejectedException())
    }

    override suspend fun apply(change: SettingChange): Result<Unit> {
        val current = session ?: return Result.failure(NotConnectedException())
        if (!supports(change)) return Result.failure(UnsupportedOperationException())
        return when (change) {
            is SettingChange.Wear ->
                writeAndConfirm(current, WearDetection.write(change.enabled), WearDetection.read()) { WearDetection.parse(it) == change.enabled }
            is SettingChange.GestureChange -> {
                val g = change.gesture
                val withInCall = change.inCall != null || profileGestures().any { it.first == g && it.second }
                writeAndConfirm(current, Gestures.write(g, change.left, change.right, change.inCall), Gestures.read(g, withInCall)) { reply ->
                    Gestures.parse(g, reply)?.let { confirms(change, it) } == true
                }
            }
            is SettingChange.EqualizerPreset ->
                writeAndConfirm(current, Equalizer.select(change.preset), Equalizer.read()) { Equalizer.parse(it)?.active == change.preset }
            is SettingChange.LowLatencyChange ->
                writeAndConfirm(current, LowLatency.write(change.enabled), LowLatency.read()) { LowLatency.parse(it) == change.enabled }
            is SettingChange.SoundQualityChange ->
                writeAndConfirm(current, SoundQuality.write(change.value), SoundQuality.read()) { SoundQuality.parse(it) == change.value }
            is SettingChange.MultipointEnabled ->
                writeAndConfirm(current, Multipoint.writeToggle(change.enabled), Multipoint.readToggle()) { Multipoint.parseToggle(it) == change.enabled }
            is SettingChange.PreferredHost ->
                writeAndCheckHosts(current, Multipoint.setPreferred(change.mac)) { hosts ->
                    hosts.any { it.mac.equals(change.mac, ignoreCase = true) && it.preferred }
                }
            is SettingChange.HostCommand ->
                writeAndCheckHosts(current, Multipoint.execute(change.action, change.mac)) { hosts ->
                    val host = hosts.firstOrNull { it.mac.equals(change.mac, ignoreCase = true) }
                    host != null && when (change.action) {
                        HostAction.CONNECT -> host.connected
                        HostAction.DISCONNECT -> !host.connected
                        HostAction.ENABLE_AUTO_CONNECT -> host.autoConnect == true
                        HostAction.DISABLE_AUTO_CONNECT -> host.autoConnect == false
                    }
                }
        }
    }

    override suspend fun refreshHosts(): Result<List<HostRow>> {
        val current = session ?: return Result.failure(NotConnectedException())
        hostCollector.reset()
        val seen = completedHostLists.value
        sendWrite(current, Multipoint.enumerate()).getOrElse { return Result.failure(it) }
        val complete = withTimeoutOrNull(hostListMillis) { completedHostLists.first { it > seen } }
        if (session !== current) return Result.failure(SessionClosedException())
        if (complete != null) return Result.success(mutable.value.hosts)
        val partial = hostCollector.partial()
        mutable.update { it.copy(hosts = partial, updatedAtMillis = clock()) }
        return Result.success(partial)
    }

    private fun supports(change: SettingChange): Boolean = when (change) {
        is SettingChange.Wear -> profile.supports("wear")
        is SettingChange.GestureChange -> profileGestures().any { it.first == change.gesture }
        is SettingChange.EqualizerPreset -> profile.supports("equalizer")
        is SettingChange.LowLatencyChange -> profile.supports("lowLatency")
        is SettingChange.SoundQualityChange -> profile.supports("soundQuality")
        is SettingChange.MultipointEnabled, is SettingChange.PreferredHost, is SettingChange.HostCommand -> profile.supports("multipoint")
    }

    /** Requested fields (null = unchanged) all match the read-back; swipe carries a single value in [GestureSetting.left]. */
    private fun confirms(change: SettingChange.GestureChange, applied: GestureSetting): Boolean =
        (change.left == null || applied.left == change.left) &&
            (change.gesture == Gesture.SWIPE || change.right == null || applied.right == change.right) &&
            (change.inCall == null || applied.inCall == change.inCall)

    /** Write, let the device settle, read back; the read-back updates state either way. */
    private suspend fun writeAndConfirm(current: DeviceSession, write: Packet, read: Packet, confirmed: (Packet) -> Boolean): Result<Unit> {
        sendWrite(current, write).getOrElse { return Result.failure(it) }
        delay(settleMillis)
        val reply = current.request(read).getOrElse { return Result.failure(it) }
        applyPacket(reply)
        return if (confirmed(reply)) Result.success(Unit) else Result.failure(AncRejectedException())
    }

    /** Host commands have no ack: write, settle, re-enumerate and check the list reflects the change. */
    private suspend fun writeAndCheckHosts(current: DeviceSession, write: Packet, confirmed: (List<HostRow>) -> Boolean): Result<Unit> {
        sendWrite(current, write).getOrElse { return Result.failure(it) }
        delay(settleMillis)
        val hosts = refreshHosts().getOrElse { return Result.failure(it) }
        return if (confirmed(hosts)) Result.success(Unit) else Result.failure(AncRejectedException())
    }

    private suspend fun sendWrite(current: DeviceSession, packet: Packet): Result<Unit> = try {
        current.send(packet)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(SessionClosedException())
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
        WearDetection.parse(packet)?.let { on -> updateSettings { it.copy(wearDetection = on) } }
        WearDetection.parseInEar(packet)?.let { inEar -> mutable.update { it.copy(inEar = inEar, updatedAtMillis = clock()) } }
        for (gesture in Gesture.entries) {
            Gestures.parse(gesture, packet)
                ?.takeIf { it.left != null || it.right != null || it.inCall != null }
                ?.let { setting -> updateSettings { it.copy(gestures = it.gestures + (gesture to setting)) } }
        }
        Equalizer.parse(packet)?.let { eq -> updateSettings { it.copy(equalizer = eq) } }
        LowLatency.parse(packet)?.let { on -> updateSettings { it.copy(lowLatency = on) } }
        SoundQuality.parse(packet)?.let { value -> updateSettings { it.copy(soundQuality = value) } }
        VoiceLanguage.parse(packet)?.let { language -> updateSettings { it.copy(language = language) } }
        Multipoint.parseToggle(packet)?.let { on -> mutable.update { it.copy(multipointEnabled = on, updatedAtMillis = clock()) } }
        Multipoint.parseRow(packet)?.let { row ->
            hostCollector.add(row)?.let { hosts ->
                mutable.update { it.copy(hosts = hosts, updatedAtMillis = clock()) }
                completedHostLists.value++
            }
        }
        if (Multipoint.isChange(packet) && profile.supports("multipoint")) {
            hostPushJob?.cancel()
            hostPushJob = scope.launch { refreshHosts() }
        }
    }

    private fun updateSettings(change: (DeviceSettings) -> DeviceSettings) {
        mutable.update { it.copy(settings = change(it.settings), updatedAtMillis = clock()) }
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
        hostPushJob?.cancel()
        hostPushJob = null
        hostCollector.reset()
        old?.close()
    }

    private companion object {
        /** Sub-keys of the profile's `gestures` capability. */
        val GESTURE_KEYS = listOf(
            "doubleTap" to Gesture.DOUBLE_TAP,
            "tripleTap" to Gesture.TRIPLE_TAP,
            "longPress" to Gesture.LONG_PRESS,
            "noiseCycle" to Gesture.NOISE_CYCLE,
            "swipe" to Gesture.SWIPE,
        )
    }
}
