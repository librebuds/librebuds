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

/** The setting did not answer its read when this session connected, so it is not offered for changes. */
class SettingUnavailableException : IllegalStateException("The earbuds did not answer this setting")

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

    /**
     * Reads every setting the profile lists; stops (without touching state) once [current] is stale.
     * These reads are unverified on hardware, so they use short, uncounted requests: a silent one
     * costs [OPTIONAL_READ_MILLIS] once and never makes the session give up. Settings whose read
     * timed out are recorded in [DeviceSettings.unanswered] (keys as in [settingKey]).
     */
    private suspend fun readSettings(current: DeviceSession, myGeneration: Int) {
        suspend fun read(key: String, packet: Packet): Boolean {
            val result = current.request(packet, timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false)
            if (!isCurrent(current, myGeneration)) return false
            result.onSuccess(::applyPacket)
            if (result.exceptionOrNull() is RequestTimeoutException) updateSettings { it.copy(unanswered = it.unanswered + key) }
            return true
        }
        if (profile.supports("wear") && !read("wear", WearDetection.read())) return
        for (entry in profileGestures()) {
            if (!read(gestureKey(entry.key), Gestures.read(entry.gesture, entry.inCall))) return
        }
        if (profile.supports("equalizer") && !read("equalizer", Equalizer.read())) return
        if (profile.supports("lowLatency") && !read("lowLatency", LowLatency.read())) return
        if (profile.supports("soundQuality") && !read("soundQuality", SoundQuality.read())) return
        if (profile.supports("language") && !read("language", VoiceLanguage.read())) return
        if (profile.supports("multipoint")) {
            if (!read("multipoint", Multipoint.readToggle())) return
            if ("multipoint" !in mutable.value.settings.unanswered) refreshHosts()
        }
    }

    private class ProfileGesture(val key: String, val gesture: Gesture, val inCall: Boolean)

    /** Gestures listed under the profile's `gestures` capability, each with whether to read its in-call action too. */
    private fun profileGestures(): List<ProfileGesture> {
        val listed = profile.capabilities["gestures"] ?: return emptyList()
        return GESTURE_KEYS.mapNotNull { (key, gesture) ->
            val entry = listed[key] ?: return@mapNotNull null
            val inCall = ((entry as? JsonObject)?.get("inCall") as? JsonPrimitive)?.booleanOrNull == true
            ProfileGesture(key, gesture, inCall)
        }
    }

    private fun gestureKey(subKey: String) = "gestures.$subKey"

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

    /**
     * Fails fast with [UnsupportedOperationException] when the profile lacks the setting,
     * [SettingUnavailableException] when its read went unanswered at connect, and
     * [IllegalArgumentException] for a [SettingChange.GestureChange] that would write nothing.
     * Auto-connect host commands are reported as success without verification when the device
     * does not include the auto-connect flag (TLV 8) in its host rows.
     */
    override suspend fun apply(change: SettingChange): Result<Unit> {
        val current = session ?: return Result.failure(NotConnectedException())
        val key = settingKey(change) ?: return Result.failure(UnsupportedOperationException())
        if (key in mutable.value.settings.unanswered) return Result.failure(SettingUnavailableException())
        if (change is SettingChange.GestureChange && writesNothing(change)) {
            return Result.failure(IllegalArgumentException("Gesture change without a value"))
        }
        return when (change) {
            is SettingChange.Wear ->
                writeAndConfirm(current, WearDetection.write(change.enabled), WearDetection.read()) { WearDetection.parse(it) == change.enabled }
            is SettingChange.GestureChange -> {
                val g = change.gesture
                val withInCall = change.inCall != null || profileGestures().any { it.gesture == g && it.inCall }
                writeAndConfirm(current, Gestures.write(g, change.left, change.right, change.inCall), Gestures.read(g, withInCall)) { reply ->
                    Gestures.parse(g, reply)?.let { confirms(change, it) } == true
                }
            }
            is SettingChange.EqualizerPreset ->
                writeAndConfirm(current, Equalizer.select(change.preset), Equalizer.read()) { Equalizer.parse(it)?.active == change.preset }
            // SPEC-GAP: low latency reads and writes share 2B/6C, and whether the write's ack carries
            // the live value or a status on TLV 2 is unknown. Waiting for the ack first keeps it from
            // being taken as the read-back's reply; a status ack may briefly show the wrong value in
            // state until the read-back lands.
            is SettingChange.LowLatencyChange ->
                writeAndConfirm(current, LowLatency.write(change.enabled), LowLatency.read(), awaitAck = true) {
                    LowLatency.parse(it) == change.enabled
                }
            is SettingChange.SoundQualityChange ->
                writeAndConfirm(current, SoundQuality.write(change.value), SoundQuality.read()) { SoundQuality.parse(it) == change.value }
            is SettingChange.MultipointEnabled ->
                writeAndConfirm(current, Multipoint.writeToggle(change.enabled), Multipoint.readToggle()) { Multipoint.parseToggle(it) == change.enabled }
            is SettingChange.PreferredHost ->
                writeAndCheckHosts(current, Multipoint.setPreferred(change.mac), attempts = 1) { hosts ->
                    hosts.any { it.mac.equals(change.mac, ignoreCase = true) && it.preferred }
                }
            is SettingChange.HostCommand -> {
                // A (dis)connection can take a few seconds on the device, so poll it; flags apply at once.
                val attempts = when (change.action) {
                    HostAction.CONNECT, HostAction.DISCONNECT -> HOST_POLL_ATTEMPTS
                    HostAction.ENABLE_AUTO_CONNECT, HostAction.DISABLE_AUTO_CONNECT -> 1
                }
                writeAndCheckHosts(current, Multipoint.execute(change.action, change.mac), attempts) { hosts ->
                    val host = hosts.firstOrNull { it.mac.equals(change.mac, ignoreCase = true) } ?: return@writeAndCheckHosts false
                    when (change.action) {
                        HostAction.CONNECT -> host.connected
                        HostAction.DISCONNECT -> !host.connected
                        // Null (no TLV 8 in the rows) means the device does not report it: unverifiable.
                        HostAction.ENABLE_AUTO_CONNECT -> host.autoConnect
                        HostAction.DISABLE_AUTO_CONNECT -> host.autoConnect?.let { !it }
                    }
                }
            }
        }
    }

    /**
     * Re-enumerates hosts. If no row at all arrives within [hostListMillis] while hosts are already
     * known, the known list is kept and the refresh fails with [RequestTimeoutException].
     */
    override suspend fun refreshHosts(): Result<List<HostRow>> {
        val current = session ?: return Result.failure(NotConnectedException())
        if (!profile.supports("multipoint")) return Result.failure(UnsupportedOperationException())
        hostCollector.reset()
        val seen = completedHostLists.value
        sendWrite(current, Multipoint.enumerate()).getOrElse { return Result.failure(it) }
        val complete = withTimeoutOrNull(hostListMillis) { completedHostLists.first { it > seen } }
        if (session !== current || current.closed.isCompleted) return Result.failure(SessionClosedException())
        if (complete != null) return Result.success(mutable.value.hosts)
        val partial = hostCollector.partial()
        if (partial.isEmpty() && mutable.value.hosts.isNotEmpty()) return Result.failure(RequestTimeoutException(Multipoint.ENUMERATE))
        mutable.update { it.copy(hosts = partial, updatedAtMillis = clock()) }
        return Result.success(partial)
    }

    /** The capability key the change belongs to (`gestures.<subKey>` for gestures), or null when the profile lacks it. */
    private fun settingKey(change: SettingChange): String? {
        val key = when (change) {
            is SettingChange.Wear -> "wear"
            is SettingChange.GestureChange -> return profileGestures().firstOrNull { it.gesture == change.gesture }?.let { gestureKey(it.key) }
            is SettingChange.EqualizerPreset -> "equalizer"
            is SettingChange.LowLatencyChange -> "lowLatency"
            is SettingChange.SoundQualityChange -> "soundQuality"
            is SettingChange.MultipointEnabled, is SettingChange.PreferredHost, is SettingChange.HostCommand -> "multipoint"
        }
        return key.takeIf { profile.supports(it) }
    }

    /** Swipe writes only [SettingChange.GestureChange.left] (mirrored) and the in-call value. */
    private fun writesNothing(change: SettingChange.GestureChange): Boolean =
        change.left == null && change.inCall == null && (change.gesture == Gesture.SWIPE || change.right == null)

    /** Requested fields (null = unchanged) all match the read-back; swipe carries a single value in [GestureSetting.left]. */
    private fun confirms(change: SettingChange.GestureChange, applied: GestureSetting): Boolean =
        (change.left == null || applied.left == change.left) &&
            (change.gesture == Gesture.SWIPE || change.right == null || applied.right == change.right) &&
            (change.inCall == null || applied.inCall == change.inCall)

    /**
     * Write, let the device settle, read back; the read-back updates state either way. With
     * [awaitAck] the write is sent as a request so its ack is consumed first (a missing ack is not
     * an error). The read-back is a short, uncounted request like the connect-time reads.
     */
    private suspend fun writeAndConfirm(
        current: DeviceSession,
        write: Packet,
        read: Packet,
        awaitAck: Boolean = false,
        confirmed: (Packet) -> Boolean,
    ): Result<Unit> {
        if (awaitAck) {
            val ack = current.request(write, timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false)
            ack.exceptionOrNull()?.let { if (it !is RequestTimeoutException) return Result.failure(it) }
        } else {
            sendWrite(current, write).getOrElse { return Result.failure(it) }
        }
        delay(settleMillis)
        val reply = current.request(read, timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false)
            .getOrElse { return Result.failure(it) }
        applyPacket(reply)
        return if (confirmed(reply)) Result.success(Unit) else Result.failure(AncRejectedException())
    }

    /**
     * Host commands have no ack: write, settle, then re-enumerate up to [attempts] times
     * ([HOST_POLL_MILLIS] apart) until the list reflects the change. [confirmed] returns null when
     * the rows cannot show the change at all, which counts as success.
     */
    private suspend fun writeAndCheckHosts(
        current: DeviceSession,
        write: Packet,
        attempts: Int,
        confirmed: (List<HostRow>) -> Boolean?,
    ): Result<Unit> {
        sendWrite(current, write).getOrElse { return Result.failure(it) }
        delay(settleMillis)
        var last: Result<Unit> = Result.failure(AncRejectedException())
        repeat(attempts) { attempt ->
            if (attempt > 0) delay(HOST_POLL_MILLIS)
            val hosts = refreshHosts().getOrElse { error ->
                if (error is RequestTimeoutException) {
                    last = Result.failure(error)
                    return@repeat
                }
                return Result.failure(error)
            }
            if (confirmed(hosts) != false) return Result.success(Unit)
            last = Result.failure(AncRejectedException())
        }
        return last
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
        /** Timeout for unverified setting reads and read-backs: one try, not counted toward give-up. */
        const val OPTIONAL_READ_MILLIS = 1200L
        const val HOST_POLL_ATTEMPTS = 3
        const val HOST_POLL_MILLIS = 2000L

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
