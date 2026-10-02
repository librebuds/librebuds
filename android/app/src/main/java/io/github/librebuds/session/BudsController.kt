// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.bt.Link
import io.github.librebuds.bt.LinkFactory
import io.github.librebuds.diag.EventLog
import io.github.librebuds.diag.FrameLog
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.command.Anc
import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.Battery
import io.github.librebuds.protocol.command.DeviceInfoCommand
import io.github.librebuds.protocol.command.EqOperation
import io.github.librebuds.protocol.command.Equalizer
import io.github.librebuds.protocol.command.Feature
import io.github.librebuds.protocol.command.FeatureState
import io.github.librebuds.protocol.command.FeatureSwitch
import io.github.librebuds.protocol.command.FindEarbuds
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.command.GestureAck
import io.github.librebuds.protocol.command.GestureSetting
import io.github.librebuds.protocol.command.Gestures
import io.github.librebuds.protocol.command.HdCall
import io.github.librebuds.protocol.command.HostAction
import io.github.librebuds.protocol.command.HostCollector
import io.github.librebuds.protocol.command.HostRow
import io.github.librebuds.protocol.command.LowLatency
import io.github.librebuds.protocol.command.Multipoint
import io.github.librebuds.protocol.command.PickupMode
import io.github.librebuds.protocol.command.Pinch
import io.github.librebuds.protocol.command.PinchSetting
import io.github.librebuds.protocol.command.RestReminder
import io.github.librebuds.protocol.command.Side
import io.github.librebuds.protocol.command.SoundQuality
import io.github.librebuds.protocol.command.Status
import io.github.librebuds.protocol.command.VoiceLanguage
import io.github.librebuds.protocol.command.WearDetection
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.FEATURE_CAPABILITIES
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.protocol.profile.ancModes
import io.github.librebuds.protocol.profile.awarenessModes
import io.github.librebuds.protocol.profile.cancellationLevels
import io.github.librebuds.protocol.profile.extendedPresets
import io.github.librebuds.protocol.profile.features
import io.github.librebuds.protocol.profile.hasAwarenessLevel
import io.github.librebuds.protocol.profile.pinchSlots
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
    private val eventLog: EventLog? = null,
    initial: BudsState = BudsState(),
) : BudsRepository {
    private val mutable = MutableStateFlow(initial)
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
        // The last known battery and noise control of the same earbuds stay visible (and stored)
        // until this session reads fresh ones; a failed attempt must not erase them.
        val previous = mutable.value.takeIf { it.address.equals(address, ignoreCase = true) }
        mutable.value = BudsState(
            link = LinkState.CONNECTING,
            address = address,
            name = name,
            battery = previous?.battery,
            anc = previous?.anc,
            device = previous?.device ?: DeviceSummary(),
            updatedAtMillis = previous?.updatedAtMillis,
        )
        event("connect ${EventLog.maskMac(address)} (generation $myGeneration)")
        val link = openLink(address, myGeneration)
        if (link == null) {
            // With audio still up after the retry, the earbuds are there but another device holds
            // the control channel.
            val next = if (isAudioConnected(address)) LinkState.TAKEN_OVER else LinkState.DISCONNECTED
            val current = generation.get() == myGeneration
            event("connect gave up: no link, ${if (current) "state $next" else "superseded"}")
            if (current) mutable.update { it.copy(link = next) }
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
        profile = registry.match(sku = info?.sku, modelId = info?.modelId, btName = name)
        event("connected, profile ${profile.id}, device info ${if (info == null) "missing" else "read"}")
        mutable.update {
            it.copy(
                link = LinkState.CONNECTED,
                profileId = profile.id,
                capabilities = profile.capabilities.keys,
                // A failed device-info read keeps the details known from an earlier connect.
                device = DeviceSummary(
                    model = profile.name,
                    firmware = info?.firmware ?: it.device.firmware,
                    serial = info?.serial ?: it.device.serial,
                ),
                updatedAtMillis = clock(),
            )
        }

        // A value carried over from before this session must not pass for a live one: a read that
        // fails on the connected session clears it.
        val batteryResult = current.request(Battery.request())
        if (!isCurrent(current, myGeneration)) return@withLock
        batteryResult.onSuccess(::applyPacket).onFailure { mutable.update { it.copy(battery = null) } }

        if (profile.supports("anc")) {
            val ancResult = current.request(Anc.readRequest())
            if (!isCurrent(current, myGeneration)) return@withLock
            ancResult.onSuccess(::applyPacket).onFailure { mutable.update { it.copy(anc = null) } }
        }

        readSettings(current, myGeneration)
    }

    /**
     * Opens the link; null when it failed. A failure while audio to [address] is up is often
     * transient (the earbuds were busy for a moment), so the open is tried once more after
     * [OPEN_RETRY_MILLIS], unless a [disconnect] or newer [connect] came in meanwhile.
     */
    private suspend fun openLink(address: String, myGeneration: Int): Link? {
        repeat(2) { attempt ->
            try {
                return linkFactory.open(address)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val audio = isAudioConnected(address)
                event("open failed (attempt ${attempt + 1}, audio ${if (audio) "up" else "down"}): ${e.javaClass.simpleName}: ${e.message}")
                if (attempt > 0 || !audio) return null
            }
            delay(OPEN_RETRY_MILLIS)
            if (generation.get() != myGeneration) return null
        }
        return null
    }

    /**
     * Reads every setting the profile lists; stops (without touching state) once [current] is stale.
     * These reads are unverified on hardware, so they use short, uncounted requests: a silent one
     * costs [OPTIONAL_READ_MILLIS] once and never makes the session give up. Settings whose read
     * timed out are recorded in [DeviceSettings.unanswered] (keys as in [settingKey]).
     */
    private suspend fun readSettings(current: DeviceSession, myGeneration: Int) {
        // Null when [current] went stale (stop), else the reply (null inside the result on a failed read).
        suspend fun request(key: String, packet: Packet, accept: (Packet) -> Boolean = { true }): Result<Packet>? {
            val result = current.request(packet, timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false, accept = accept)
            if (!isCurrent(current, myGeneration)) return null
            result.onSuccess(::applyPacket)
            if (result.exceptionOrNull() is RequestTimeoutException) updateSettings { it.copy(unanswered = it.unanswered + key) }
            return result
        }
        suspend fun read(key: String, packet: Packet): Boolean = request(key, packet) != null
        fun unsupported(key: String) = updateSettings { it.copy(unsupported = it.unsupported + key) }
        if (profile.supports("wear") && !read("wear", WearDetection.read())) return
        for (entry in profileGestures()) {
            if (!read(gestureKey(entry.key), Gestures.read(entry.gesture, entry.inCall))) return
        }
        if (profile.supports("equalizer") && !read("equalizer", Equalizer.read())) return
        if (profile.supports("lowLatency")) {
            // The vendor app's support query comes first, on its own: no TLV 3 means no such setting.
            val probe = request("lowLatency", LowLatency.probe()) ?: return
            val variant = probe.getOrNull()?.let(LowLatency::parseSupport)
            if (variant == null) {
                if (probe.isSuccess) unsupported("lowLatency")
            } else {
                updateSettings { it.copy(lowLatencyDynamic = variant == 1) }
                if (!read("lowLatency", LowLatency.read())) return
            }
        }
        if (profile.supports("soundQuality")) {
            val reply = request("soundQuality", SoundQuality.read()) ?: return
            // The switch exists only with a capability of 1 or more on TLV 1.
            reply.getOrNull()?.let { packet -> if ((SoundQuality.parseCapability(packet) ?: 0) < 1) unsupported("soundQuality") }
        }
        if (profile.supports("language") && !read("language", VoiceLanguage.read())) return
        if (profile.supports("multipoint")) {
            if (!read("multipoint", Multipoint.readToggle())) return
            if ("multipoint" !in mutable.value.settings.unanswered) refreshHosts()
        }
        readFeatures(current, ::request)
    }

    /**
     * The read-only probe round for the per-model extras, each sent only when the profile lists the
     * feature: the feature-switch ability query, then the state of every feature it offers (and the
     * ear tip type, which has no ability entry); every pinch slot; the extended equalizer presets;
     * rest reminder, HD calls and pickup mode; each side's find-earbuds sound state. [request]
     * returns null once [current] went stale.
     */
    private suspend fun readFeatures(
        current: DeviceSession,
        request: suspend (String, Packet, (Packet) -> Boolean) -> Result<Packet>?,
    ) {
        val features = profile.features()
        if (features.any { it.capability != null }) {
            val reply = request("abilities", FeatureSwitch.abilityQuery()) { true } ?: return
            val abilities = reply.getOrNull()?.let(FeatureSwitch::parseAbilities)
            event("feature abilities ${abilities?.capabilities?.keys?.sorted()?.joinToString(",") { "%02X".format(it) } ?: "not answered"}")
            // The earbuds ask to be queried again; the vendor app then sends the query with TLV 1 = 0.
            if (abilities?.needsReply == true) sendWrite(current, FeatureSwitch.abilityQuery(first = false))
        }
        for (feature in features) {
            if (feature.capability != null && mutable.value.settings.abilities?.offers(feature) != true) continue
            request("feature.${feature.name}", FeatureSwitch.read(feature)) { FeatureSwitch.answers(feature, it) } ?: return
        }
        for (spec in profile.pinchSlots()) {
            request("pinch.${spec.slot}", Pinch.read(spec.slot)) { Pinch.answers(spec.slot, it) } ?: return
        }
        if (profile.supports("equalizer") && profile.extendedPresets().isNotEmpty()) {
            request("equalizerExtended", Equalizer.extendedQuery()) { true } ?: return
        }
        if (profile.supports("restReminder")) request("restReminder", RestReminder.read()) { true } ?: return
        if (profile.supports("hdCall")) request("hdCall", HdCall.read()) { true } ?: return
        if (profile.supports("pickupMode")) request("pickupMode", PickupMode.read()) { true } ?: return
        if (profile.supports("findEarbuds")) {
            for (side in Side.entries) {
                request("findEarbuds", FindEarbuds.query(side)) { FindEarbuds.parseState(it)?.first == side } ?: return
            }
        }
    }

    private class ProfileGesture(val key: String, val gesture: Gesture, val inCall: Boolean, val bothSides: Boolean)

    /**
     * Gestures listed under the profile's `gestures` capability, each with whether to read its
     * in-call action too and whether it has one value for both earbuds.
     */
    private fun profileGestures(): List<ProfileGesture> {
        val listed = profile.capabilities["gestures"] ?: return emptyList()
        return GESTURE_SUB_KEYS.mapNotNull { (key, gesture) ->
            val entry = listed[key] as? JsonObject ?: return@mapNotNull null
            fun flag(name: String) = (entry[name] as? JsonPrimitive)?.booleanOrNull == true
            ProfileGesture(key, gesture, flag("inCall"), gesture == Gesture.SWIPE || flag("bothSides"))
        }
    }

    private fun gestureKey(subKey: String) = "gestures.$subKey"

    /**
     * Moves a TAKEN_OVER state without a session to DISCONNECTED unless [keepWhile] says the
     * earbuds still have audio to this phone; never connects. Used once the audio state is known
     * after a restart, and when the earbuds go away while the connection service is not running.
     */
    fun clearTakeOver(keepWhile: (String) -> Boolean = { false }) {
        if (session != null) return
        mutable.update { state ->
            val keep = state.link != LinkState.TAKEN_OVER || state.address?.let(keepWhile) == true
            if (keep) state else state.copy(link = LinkState.DISCONNECTED)
        }
    }

    fun disconnect() {
        event("disconnect requested (link ${mutable.value.link})")
        generation.incrementAndGet()
        closeSession()
        mutable.update { it.copy(link = LinkState.DISCONNECTED) }
    }

    /** Fails fast with [UnsupportedOperationException] for a mode the profile does not list (a stale widget button, say). */
    override suspend fun setAnc(mode: AncMode): Result<AncState> {
        val current = session ?: return Result.failure(NotConnectedException())
        if (mode !in profile.ancModes()) {
            event("anc mode ${mode.code} not offered by profile ${profile.id}")
            return Result.failure(UnsupportedOperationException())
        }
        val level = levelFor(mode, mutable.value.anc, profile)
        return writeAnc(current, mode, level) { Anc.confirms(it, mode) }
    }

    /** Fails fast with [UnsupportedOperationException] for a level the profile does not list. */
    override suspend fun setAncLevel(level: Int): Result<AncState> {
        val current = session ?: return Result.failure(NotConnectedException())
        if (level !in profile.cancellationLevels()) {
            event("anc level $level not offered by profile ${profile.id}")
            return Result.failure(UnsupportedOperationException())
        }
        return writeAnc(current, AncMode.CANCELLATION, level) { Anc.confirmsLevel(it, level) }
    }

    /** Writes [mode] and [level], lets the device settle, reads back; the read-back updates state either way. */
    private suspend fun writeAnc(current: DeviceSession, mode: AncMode, level: Int, confirmed: (AncState) -> Boolean): Result<AncState> {
        event("anc write mode=${mode.code} level=$level")
        sendWrite(current, Anc.writeRequest(mode, level)).getOrElse { event("anc write failed: ${it.javaClass.simpleName}"); return Result.failure(it) }
        delay(settleMillis)
        val reply = current.request(Anc.readRequest()).getOrElse { event("anc read-back failed: ${it.javaClass.simpleName}"); return Result.failure(it) }
        applyPacket(reply)
        val applied = Anc.parseState(reply) ?: return Result.failure(AncRejectedException())
        event("anc read-back mode=${applied.modeCode} level=${applied.level}")
        return if (confirmed(applied)) Result.success(applied) else Result.failure(AncRejectedException())
    }

    /**
     * Fails fast with [UnsupportedOperationException] when the profile lacks the setting,
     * [SettingUnavailableException] when its read went unanswered at connect, and
     * [IllegalArgumentException] for a [SettingChange.GestureChange] that would write nothing or a
     * host change whose MAC is malformed.
     * Auto-connect host commands are reported as success without verification when the device
     * does not include the auto-connect flag (TLV 8) in its host rows.
     */
    override suspend fun apply(change: SettingChange): Result<Unit> {
        val current = session ?: return Result.failure(NotConnectedException())
        val key = settingKey(change) ?: return Result.failure(UnsupportedOperationException())
        val settings = mutable.value.settings
        if (key in settings.unanswered || key in settings.unsupported) return Result.failure(SettingUnavailableException())
        if (change is SettingChange.GestureChange && writesNothing(change)) {
            return Result.failure(IllegalArgumentException("Gesture change without a value"))
        }
        return when (change) {
            is SettingChange.Wear ->
                writeAndConfirm(current, WearDetection.write(change.enabled), WearDetection.read()) { WearDetection.parse(it) == change.enabled }
            is SettingChange.GestureChange -> writeGesture(current, change)
            is SettingChange.EqualizerPreset -> writeEqualizer(current, change.preset)
            // Reads and writes share 2B/6C; the write is answered with a status record, which decides.
            // A reply without one (an older firmware echoing the value) falls back to the read-back.
            is SettingChange.LowLatencyChange ->
                writeAndConfirm(
                    current,
                    LowLatency.write(change.enabled),
                    LowLatency.read(),
                    ack = { reply -> Status.of(reply)?.let { it == Status.SUCCESS } },
                    onAccepted = { updateSettings("lowLatency") { it.copy(lowLatency = change.enabled) } },
                ) { LowLatency.parse(it) == change.enabled }
            is SettingChange.SoundQualityChange ->
                writeAndConfirm(current, SoundQuality.write(change.value), SoundQuality.read()) { SoundQuality.parse(it) == change.value }
            is SettingChange.MultipointEnabled ->
                writeAndConfirm(current, Multipoint.writeToggle(change.enabled), Multipoint.readToggle()) { Multipoint.parseToggle(it) == change.enabled }
            is SettingChange.PreferredHost ->
                writeAndCheckHosts(current, hostPacket { Multipoint.setPreferred(change.mac) } ?: return malformedMac(), attempts = 1) { hosts ->
                    hosts.any { it.mac.equals(change.mac, ignoreCase = true) && it.preferred }
                }
            is SettingChange.FeatureValue -> writeFeature(current, change)
            is SettingChange.PinchChange -> writePinch(current, change)
            is SettingChange.AwarenessMode -> {
                if (change.subMode !in profile.awarenessModes()) return Result.failure(UnsupportedOperationException())
                writeAnc(current, AncMode.AWARENESS, change.subMode) { Anc.confirmsAwareness(it, change.subMode) }.map { }
            }
            is SettingChange.AwarenessLevel -> writeAwarenessLevel(current, change.level)
            is SettingChange.CustomEqualizer -> writeCustomEqualizer(current, change)
            is SettingChange.RestReminderChange ->
                writeAndConfirm(current, RestReminder.write(if (change.enabled) 1 else 0), RestReminder.read(), ack = RestReminder::parseAck,
                    onAccepted = { updateSettings("restReminder") { it.copy(restReminder = change.enabled) } }) { RestReminder.parse(it) == (if (change.enabled) 1 else 0) }
            is SettingChange.HdCallChange ->
                writeAndConfirm(current, HdCall.write(if (change.enabled) 1 else 0), HdCall.read(), ack = HdCall::parseAck,
                    onAccepted = { updateSettings("hdCall") { it.copy(hdCall = change.enabled) } }) { HdCall.parse(it) == (if (change.enabled) 1 else 0) }
            is SettingChange.PickupModeChange ->
                writeAndConfirm(current, PickupMode.write(change.mode), PickupMode.read(), ack = PickupMode::parseAck,
                    onAccepted = { updateSettings("pickupMode") { it.copy(pickupMode = change.mode) } }) { PickupMode.parse(it) == change.mode }
            is SettingChange.HostCommand -> {
                // A (dis)connection can take a few seconds on the device, so poll it; flags apply at once.
                val attempts = when (change.action) {
                    HostAction.CONNECT, HostAction.DISCONNECT -> HOST_POLL_ATTEMPTS
                    HostAction.ENABLE_AUTO_CONNECT, HostAction.DISABLE_AUTO_CONNECT -> 1
                }
                val packet = hostPacket { Multipoint.execute(change.action, change.mac) } ?: return malformedMac()
                writeAndCheckHosts(current, packet, attempts) { hosts ->
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

    /** The host command's packet, or null when its MAC cannot be encoded. */
    private fun hostPacket(build: () -> Packet): Packet? = try {
        build()
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun malformedMac(): Result<Unit> = Result.failure(IllegalArgumentException("Malformed MAC address"))

    /** The capability key the change belongs to (`gestures.<subKey>` for gestures), or null when the profile lacks it. */
    private fun settingKey(change: SettingChange): String? {
        val key = when (change) {
            is SettingChange.Wear -> "wear"
            is SettingChange.GestureChange -> return profileGestures().firstOrNull { it.gesture == change.gesture }?.let { gestureKey(it.key) }
            is SettingChange.EqualizerPreset -> "equalizer"
            is SettingChange.LowLatencyChange -> "lowLatency"
            is SettingChange.SoundQualityChange -> "soundQuality"
            is SettingChange.MultipointEnabled, is SettingChange.PreferredHost, is SettingChange.HostCommand -> "multipoint"
            is SettingChange.FeatureValue -> FEATURE_CAPABILITIES.getValue(change.feature)
            is SettingChange.PinchChange -> "pinch"
            is SettingChange.AwarenessMode -> "anc"
            is SettingChange.AwarenessLevel -> return "anc".takeIf { profile.hasAwarenessLevel() }
            is SettingChange.CustomEqualizer -> "equalizer"
            is SettingChange.RestReminderChange -> "restReminder"
            is SettingChange.HdCallChange -> "hdCall"
            is SettingChange.PickupModeChange -> "pickupMode"
        }
        return key.takeIf { profile.supports(it) }
    }

    private fun bothSides(gesture: Gesture): Boolean =
        gesture == Gesture.SWIPE || profileGestures().any { it.gesture == gesture && it.bothSides }

    /** A single-value gesture writes only [SettingChange.GestureChange.left] and the in-call value. */
    private fun writesNothing(change: SettingChange.GestureChange): Boolean =
        change.left == null && change.inCall == null && (bothSides(change.gesture) || change.right == null)

    /** Requested fields (null = unchanged) all match the read-back; a single-value gesture carries it in [GestureSetting.left]. */
    private fun confirms(change: SettingChange.GestureChange, applied: GestureSetting, both: Boolean): Boolean =
        (change.left == null || applied.left == change.left) &&
            (both || change.right == null || applied.right == change.right) &&
            (change.inCall == null || applied.inCall == change.inCall)

    /**
     * Gesture writes go one side per frame, as the vendor app sends them, and each frame's ack
     * (see [Gestures.parseAck]) decides: a refused side fails the change with [AncRejectedException]
     * and leaves the earlier frames' sides as the device reported them. Only when a frame gets no
     * ack at all does a read-back decide.
     */
    private suspend fun writeGesture(current: DeviceSession, change: SettingChange.GestureChange): Result<Unit> {
        val g = change.gesture
        val both = bothSides(g)
        val withInCall = change.inCall != null || profileGestures().any { it.gesture == g && it.inCall }
        val frames = buildList {
            if (both) {
                change.left?.let { add(Gestures.write(g, left = it, bothSides = true)) }
            } else {
                change.left?.let { add(Gestures.write(g, left = it)) }
                change.right?.let { add(Gestures.write(g, right = it)) }
            }
            change.inCall?.let { add(Gestures.write(g, inCall = it)) }
        }
        var unconfirmed = false
        for (frame in frames) {
            val reply = current.request(frame, timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false)
            reply.exceptionOrNull()?.let { if (it !is RequestTimeoutException) return Result.failure(it) }
            when (reply.getOrNull()?.let { Gestures.parseAck(g, it) }) {
                GestureAck.REJECTED -> {
                    event("gesture ${g.name} write refused by the earbuds")
                    return Result.failure(AncRejectedException())
                }
                GestureAck.ACCEPTED -> applyGesture(g, frame, both)
                null -> unconfirmed = true
            }
        }
        if (!unconfirmed) return Result.success(Unit)
        delay(settleMillis)
        val reply = current.request(Gestures.read(g, withInCall), timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false)
            .getOrElse { return Result.failure(it) }
        applyPacket(reply)
        return if (Gestures.parse(g, reply)?.let { confirms(change, it, both) } == true) Result.success(Unit) else Result.failure(AncRejectedException())
    }

    /** Puts the values of an acknowledged write [frame] into state. */
    private fun applyGesture(g: Gesture, frame: Packet, both: Boolean) {
        val subKey = GESTURE_SUB_KEYS.firstOrNull { it.second == g }?.first ?: return
        fun value(type: Int) = frame.find(type)?.takeIf { it.size == 1 }?.get(0)?.toInt()
        updateSettings(gestureKey(subKey)) { settings ->
            val old = settings.gestures[g] ?: return@updateSettings settings
            val left = value(1) ?: old.left
            // A single-value frame told both earbuds the same value.
            val next = old.copy(left = left, right = if (both) left else value(2) ?: old.right, inCall = value(4) ?: old.inCall)
            settings.copy(gestures = settings.gestures + (g to next))
        }
    }

    /**
     * Selects a preset. The earbuds answer later with their own 2B/49 frame carrying a status;
     * success there is the confirmation (the vendor app goes by it alone), so the read-back that
     * follows only refreshes the list and never reverts the preset. A refusal fails with
     * [AncRejectedException]; with no status at all the read-back decides.
     */
    private suspend fun writeEqualizer(current: DeviceSession, preset: Int): Result<Unit> {
        // Extended presets and custom slots are selected with the full write, as the vendor app does.
        val custom = mutableSettingsValue().equalizer?.custom?.firstOrNull { it.id == preset }
        val frame = profile.extendedPresets()[preset]?.let { Equalizer.selectExtended(preset, it) }
            ?: custom?.let { Equalizer.writeCustom(it.id, it.gains, it.name.ifEmpty { it.id.toString() }, EqOperation.SAVE) }
            ?: Equalizer.select(preset)
        val reply = current.request(frame, timeoutMillis = EQUALIZER_ACK_MILLIS, retries = 0, countsTowardGiveUp = false)
        reply.exceptionOrNull()?.let { if (it !is RequestTimeoutException) return Result.failure(it) }
        val ack = reply.getOrNull()?.let(Equalizer::parseAck)
        if (ack == false) {
            event("equalizer preset $preset refused (status ${reply.getOrNull()?.let(Status::of)})")
            return Result.failure(AncRejectedException())
        }
        if (ack == true) updateSettings("equalizer") { s -> s.copy(equalizer = s.equalizer?.copy(active = preset)) }
        delay(settleMillis)
        val read = current.request(Equalizer.read(), timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false)
        if (ack == true) {
            read.getOrNull()?.let(Equalizer::parse)?.let { fresh ->
                if (fresh.active != preset) event("equalizer read-back reports ${fresh.active} after preset $preset was acknowledged")
                updateSettings("equalizer") { s -> s.copy(equalizer = fresh.copy(active = preset)) }
            }
            return Result.success(Unit)
        }
        val packet = read.getOrElse { return Result.failure(it) }
        applyPacket(packet)
        return if (Equalizer.parse(packet)?.active == preset) Result.success(Unit) else Result.failure(AncRejectedException())
    }

    /**
     * A feature switch write (2B/B4). The earbuds answer with the feature's new state, which decides;
     * without a matching answer a read-back does.
     */
    private suspend fun writeFeature(current: DeviceSession, change: SettingChange.FeatureValue): Result<Unit> {
        val feature = change.feature
        val settings = mutableSettingsValue()
        val available = if (feature.capability != null) settings.abilities?.offers(feature) == true else settings.features[feature]?.state != null
        if (!available) return Result.failure(SettingUnavailableException())
        fun applied(state: FeatureState?): Boolean? = when (change.field) {
            2 -> state?.state
            FeatureSwitch.NOD -> state?.first
            else -> state?.second
        }?.let { it == change.value }
        event("feature ${feature.name} field ${change.field} write ${change.value}")
        val reply = current.request(
            FeatureSwitch.writeField(feature, change.field, change.value),
            timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false,
        ) { FeatureSwitch.answers(feature, it) }
        reply.exceptionOrNull()?.let { if (it !is RequestTimeoutException) return Result.failure(it) }
        if (reply.getOrNull()?.let { applied(FeatureSwitch.parseState(it)) } == true) return Result.success(Unit)
        delay(settleMillis)
        val read = current.request(FeatureSwitch.read(feature), timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false) {
            FeatureSwitch.answers(feature, it)
        }.getOrElse { return Result.failure(it) }
        val ok = applied(FeatureSwitch.parseState(read)) == true
        event("feature ${feature.name} read-back ${if (ok) "confirms" else "differs"}")
        return if (ok) Result.success(Unit) else Result.failure(AncRejectedException())
    }

    /**
     * A pinch write. Tap slots send one frame with the same action for both earbuds; pinch and hold
     * one frame per side. The vendor app does not judge the reply, so a re-read of the slot confirms
     * it (a reply with a non-success status still fails the change).
     */
    private suspend fun writePinch(current: DeviceSession, change: SettingChange.PinchChange): Result<Unit> {
        val slot = change.slot
        if (profile.pinchSlots().none { it.slot == slot }) return Result.failure(UnsupportedOperationException())
        if (mutableSettingsValue().pinch[slot] == null) return Result.failure(SettingUnavailableException())
        val frames = if (slot.type == PINCH_AND_HOLD) {
            listOfNotNull(change.left?.let { Pinch.write(slot, it, null) }, change.right?.let { Pinch.write(slot, null, it) })
        } else {
            listOfNotNull(change.left?.let { Pinch.write(slot, it, it) })
        }
        if (frames.isEmpty()) return Result.failure(IllegalArgumentException("Pinch change without a value"))
        event("pinch $slot write left=${change.left} right=${change.right}")
        for (frame in frames) {
            val reply = current.request(frame, timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false)
            reply.exceptionOrNull()?.let { if (it !is RequestTimeoutException) return Result.failure(it) }
            val status = reply.getOrNull()?.let(Status::of)
            if (status != null && status != Status.SUCCESS) {
                event("pinch $slot write refused (status $status)")
                return Result.failure(AncRejectedException())
            }
        }
        delay(settleMillis)
        val read = current.request(Pinch.read(slot), timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false) {
            Pinch.answers(slot, it)
        }.getOrElse { return Result.failure(it) }
        val applied = Pinch.parse(read)
        val ok = applied != null && (change.left == null || applied.left == change.left) &&
            (change.right == null || applied.right == change.right) &&
            (slot.type == PINCH_AND_HOLD || applied.right == change.left)
        event("pinch $slot read-back left=${applied?.left} right=${applied?.right}")
        return if (ok) Result.success(Unit) else Result.failure(AncRejectedException())
    }

    /** The adaptive awareness slider: the write's acknowledgement first, then a read-back of TLV 2. */
    private suspend fun writeAwarenessLevel(current: DeviceSession, level: Int): Result<Unit> {
        if (level !in 0..Anc.AWARENESS_LEVEL_MAX) return Result.failure(IllegalArgumentException("Awareness level out of range"))
        event("awareness level write $level")
        val reply = current.request(Anc.awarenessLevelRequest(level), timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false)
        reply.exceptionOrNull()?.let { if (it !is RequestTimeoutException) return Result.failure(it) }
        if (reply.getOrNull()?.let(Anc::isWriteAccepted) == false) return Result.failure(AncRejectedException())
        delay(settleMillis)
        val read = current.request(Anc.readRequest()).getOrElse { return Result.failure(it) }
        applyPacket(read)
        val applied = Anc.parseState(read)
        event("awareness level read-back ${applied?.awarenessLevel}")
        return if (applied?.awarenessLevel == level) Result.success(Unit) else Result.failure(AncRejectedException())
    }

    /**
     * A custom preset write. The earbuds' status decides as for a preset select; a save or delete is
     * then checked against the re-read list of custom presets.
     */
    private suspend fun writeCustomEqualizer(current: DeviceSession, change: SettingChange.CustomEqualizer): Result<Unit> {
        if (change.id !in Equalizer.CUSTOM_IDS) return Result.failure(IllegalArgumentException("Not a custom preset slot"))
        val frame = try {
            Equalizer.writeCustom(change.id, change.gains, change.name, change.operation)
        } catch (e: IllegalArgumentException) {
            return Result.failure(e)
        }
        event("custom equalizer ${change.id} ${change.operation.name}")
        val reply = current.request(frame, timeoutMillis = EQUALIZER_ACK_MILLIS, retries = 0, countsTowardGiveUp = false)
        reply.exceptionOrNull()?.let { if (it !is RequestTimeoutException) return Result.failure(it) }
        val ack = reply.getOrNull()?.let(Equalizer::parseAck)
        if (ack == false) {
            event("custom equalizer ${change.id} refused (status ${reply.getOrNull()?.let(Status::of)})")
            return Result.failure(AncRejectedException())
        }
        if (change.operation == EqOperation.PREVIEW) return Result.success(Unit)
        delay(settleMillis)
        val read = current.request(Equalizer.read(), timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false)
            .getOrElse { return if (ack == true) Result.success(Unit) else Result.failure(it) }
        val listed = Equalizer.parse(read)?.custom?.any { it.id == change.id }
        val ok = if (change.operation == EqOperation.SAVE) listed == true else listed == false
        event("custom equalizer ${change.id} read-back ${if (ok) "confirms" else "differs"}")
        return if (ok || ack == true) Result.success(Unit) else Result.failure(AncRejectedException())
    }

    /**
     * Rings or silences one earbud. The earbuds answer on 2B/5D with [side, result] (0 = done) and
     * report the sound state on 2B/5E; without an answer a state query decides.
     */
    override suspend fun ring(side: Side, ring: Boolean): Result<Unit> {
        val current = session ?: return Result.failure(NotConnectedException())
        if (!profile.supports("findEarbuds")) return Result.failure(UnsupportedOperationException())
        event("find earbuds ${side.name} ${if (ring) "ring" else "stop"}")
        val frame = if (ring) FindEarbuds.ring(side) else FindEarbuds.stop(side)
        val reply = current.request(frame, timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false) { FindEarbuds.answers(side, it) }
        reply.exceptionOrNull()?.let { if (it !is RequestTimeoutException) return Result.failure(it) }
        val packet = reply.getOrNull()
        val status = packet?.let(Status::of)
        if (status != null && status != Status.SUCCESS) return Result.failure(AncRejectedException())
        val result = packet?.let(FindEarbuds::parseResult)?.result
        if (result == 0) {
            updateSettings("findEarbuds") { it.copy(ringing = it.ringing + (side to ring)) }
            return Result.success(Unit)
        }
        if (result != null) {
            event("find earbuds ${side.name} refused (result $result)")
            return Result.failure(AncRejectedException())
        }
        val state = current.request(FindEarbuds.query(side), timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false) {
            FindEarbuds.parseState(it)?.first == side
        }.getOrElse { return Result.failure(it) }
        return if (FindEarbuds.parseState(state)?.second == ring) Result.success(Unit) else Result.failure(AncRejectedException())
    }

    private fun mutableSettingsValue() = mutable.value.settings

    /**
     * Write, let the device settle, read back; the read-back updates state either way. With
     * [ack] the write is sent as a request and its reply judged first: true means applied
     * ([onAccepted] runs, no read-back), false refused, null (or no reply) leaves it to the read-back.
     * The read-back is a short, uncounted request like the connect-time reads.
     */
    private suspend fun writeAndConfirm(
        current: DeviceSession,
        write: Packet,
        read: Packet,
        ack: ((Packet) -> Boolean?)? = null,
        onAccepted: () -> Unit = {},
        confirmed: (Packet) -> Boolean,
    ): Result<Unit> {
        if (ack != null) {
            val reply = current.request(write, timeoutMillis = OPTIONAL_READ_MILLIS, retries = 0, countsTowardGiveUp = false)
            reply.exceptionOrNull()?.let { if (it !is RequestTimeoutException) return Result.failure(it) }
            when (reply.getOrNull()?.let(ack)) {
                true -> {
                    onAccepted()
                    return Result.success(Unit)
                }
                false -> return Result.failure(AncRejectedException())
                null -> Unit
            }
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
        WearDetection.parse(packet)?.let { on -> updateSettings("wear") { it.copy(wearDetection = on) } }
        WearDetection.parseInEar(packet)?.let { inEar -> mutable.update { it.copy(inEar = inEar, updatedAtMillis = clock()) } }
        for ((subKey, gesture) in GESTURE_SUB_KEYS) {
            Gestures.parse(gesture, packet)
                ?.takeIf { it.left != null || it.right != null || it.inCall != null }
                ?.let { setting -> updateSettings(gestureKey(subKey)) { it.copy(gestures = it.gestures + (gesture to setting)) } }
        }
        Equalizer.parse(packet)?.let { eq -> updateSettings("equalizer") { it.copy(equalizer = eq) } }
        LowLatency.parse(packet)?.let { on -> updateSettings("lowLatency") { it.copy(lowLatency = on) } }
        SoundQuality.parse(packet)?.let { value -> updateSettings("soundQuality") { it.copy(soundQuality = value) } }
        VoiceLanguage.parse(packet)?.let { language -> updateSettings("language") { it.copy(language = language) } }
        FeatureSwitch.parseAbilities(packet)?.let { abilities -> updateSettings("abilities") { it.copy(abilities = abilities) } }
        FeatureSwitch.parseState(packet)?.let { state ->
            Feature.ofKey(state.key)?.let { feature ->
                updateSettings("feature.${feature.name}") { s -> s.copy(features = s.features + (feature to merge(s.features[feature], state))) }
            }
        }
        Pinch.parse(packet)?.let { pinch ->
            updateSettings("pinch.${pinch.slot}") { s ->
                val old = s.pinch[pinch.slot]
                s.copy(pinch = s.pinch + (pinch.slot to PinchSetting(pinch.slot, pinch.left ?: old?.left, pinch.right ?: old?.right)))
            }
        }
        Equalizer.parseExtended(packet)?.let { on -> updateSettings("equalizerExtended") { it.copy(equalizerExtended = on) } }
        RestReminder.parse(packet)?.let { value -> updateSettings("restReminder") { it.copy(restReminder = value == 1) } }
        HdCall.parse(packet)?.let { value -> updateSettings("hdCall") { it.copy(hdCall = value == 1) } }
        PickupMode.parse(packet)?.let { value -> updateSettings("pickupMode") { it.copy(pickupMode = value) } }
        FindEarbuds.parseState(packet)?.let { (side, ringing) -> updateSettings("findEarbuds") { it.copy(ringing = it.ringing + (side to ringing)) } }
        Multipoint.parseToggle(packet)?.let { on ->
            mutable.update {
                it.copy(multipointEnabled = on, settings = it.settings.answered("multipoint"), updatedAtMillis = clock())
            }
        }
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

    /**
     * Applies [change] to the settings. [answered] is the key of the setting whose value just
     * arrived: a reply that comes in after its read timed out still makes the setting available.
     */
    private fun updateSettings(answered: String? = null, change: (DeviceSettings) -> DeviceSettings) {
        mutable.update {
            val next = change(it.settings)
            it.copy(settings = if (answered == null) next else next.answered(answered), updatedAtMillis = clock())
        }
    }

    private fun DeviceSettings.answered(key: String) = if (key in unanswered) copy(unanswered = unanswered - key) else this

    /** A feature answer may carry only some fields (a write's reply, say); the others keep their last value. */
    private fun merge(old: FeatureState?, new: FeatureState): FeatureState =
        FeatureState(new.key, new.state ?: old?.state, new.first ?: old?.first, new.second ?: old?.second)

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
            event("session gave up: no reply, state DISCONNECTED")
            mutable.update { it.copy(link = LinkState.DISCONNECTED, lastError = LinkError.NO_REPLY) }
            return
        }
        val next = if (isAudioConnected(address)) LinkState.TAKEN_OVER else LinkState.DISCONNECTED
        event("session closed (${reason?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "clean"}), state $next")
        mutable.update { it.copy(link = next) }
    }

    private fun event(msg: String) {
        eventLog?.record(TAG, msg)
        // Same line in logcat, so a release build can be followed with `adb logcat -s BudsController`.
        try { android.util.Log.i(TAG, EventLog.maskMacs(msg)) } catch (_: RuntimeException) { }
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
        const val TAG = "BudsController"

        /** Timeout for unverified setting reads and read-backs: one try, not counted toward give-up. */
        const val OPTIONAL_READ_MILLIS = 1200L

        /** The equalizer's select reply comes asynchronously, so it gets a longer wait. */
        const val EQUALIZER_ACK_MILLIS = 3000L
        const val HOST_POLL_ATTEMPTS = 3
        const val HOST_POLL_MILLIS = 2000L

        /** Pause before the second link open when the first failed while audio was up. */
        const val OPEN_RETRY_MILLIS = 1000L

        /** Pinch type of pinch and hold, which is written one side per frame. */
        const val PINCH_AND_HOLD = 3
    }
}

/**
 * Sub-keys of the profile's `gestures` capability, in display order. The single list both the
 * controller (what to read) and the settings screen (what to show) go by.
 */
internal val GESTURE_SUB_KEYS: List<Pair<String, Gesture>> = listOf(
    "doubleTap" to Gesture.DOUBLE_TAP,
    "tripleTap" to Gesture.TRIPLE_TAP,
    "longPress" to Gesture.LONG_PRESS,
    "noiseCycle" to Gesture.NOISE_CYCLE,
    "swipe" to Gesture.SWIPE,
)
