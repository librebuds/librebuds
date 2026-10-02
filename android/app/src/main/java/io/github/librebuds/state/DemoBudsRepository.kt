// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.protocol.command.CustomPreset
import io.github.librebuds.protocol.command.EqualizerState
import io.github.librebuds.protocol.command.Feature
import io.github.librebuds.protocol.command.FeatureAbilities
import io.github.librebuds.protocol.command.FeatureState
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.command.GestureSetting
import io.github.librebuds.protocol.command.HostRow
import io.github.librebuds.protocol.command.LanguageInfo
import io.github.librebuds.protocol.command.PinchSetting
import io.github.librebuds.protocol.command.PinchSlot
import io.github.librebuds.protocol.command.Side
import io.github.librebuds.ui.model.applyTo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The address the demo earbuds report; never a real device. */
const val DEMO_ADDRESS = "00:00:00:00:00:00"

/** The demo earbuds' name, as shown on the device screen and in the pair switcher. */
const val DEMO_NAME = "Demo earbuds"

/**
 * In-memory earbuds for UI work without hardware (debug builds, demo mode). Every per-model
 * setting and two multipoint hosts are seeded so all sections can be seen; changes only update
 * this in-memory state. Nothing here reaches the Bluetooth controller or the stored state.
 */
class DemoBudsRepository(
    private val clock: () -> Long = System::currentTimeMillis,
    private val applyDelayMillis: Long = 800,
) : BudsRepository {
    private val mutable = MutableStateFlow(
        BudsState(
            link = LinkState.CONNECTED,
            address = DEMO_ADDRESS,
            name = DEMO_NAME,
            profileId = "freebuds-6",
            capabilities = setOf("battery", "anc", "wear", "gestures", "equalizer", "lowLatency", "soundQuality", "multipoint", "language"),
            battery = BatteryState(90, 100, 85, 60, false, false, true),
            anc = AncState(modeCode = AncMode.OFF.code, level = 3),
            device = DeviceSummary(model = "Demo earbuds", firmware = "1.0.0", serial = "DEMO0001"),
            updatedAtMillis = clock(),
            settings = DeviceSettings(
                wearDetection = true,
                gestures = mapOf(
                    Gesture.DOUBLE_TAP to GestureSetting(left = 1, right = 2, inCall = 0, supported = listOf(-1, 0, 1, 2, 7)),
                    Gesture.TRIPLE_TAP to GestureSetting(left = 2, right = 7, inCall = null, supported = listOf(-1, 0, 1, 2, 7)),
                    Gesture.LONG_PRESS to GestureSetting(left = 3, right = 3, inCall = 0, supported = listOf(-1, 0, 3, 15), inCallSupported = listOf(0, -1)),
                    Gesture.NOISE_CYCLE to GestureSetting(left = 2, right = 2, inCall = null, supported = listOf(1, 2, 3, 4)),
                    Gesture.SWIPE to GestureSetting(left = 0, right = 0, inCall = null, supported = listOf(-1, 0)),
                ),
                equalizer = EqualizerState(active = 1, available = listOf(1, 2, 3, 9), custom = listOf(CustomPreset(100, listOf(20, 10, 0, 0, 0, 0, 0, 10, 20, 30), "Demo"))),
                lowLatency = false,
                soundQuality = 1,
                language = LanguageInfo(current = "en-GB", supported = listOf("en-GB", "de-DE")),
                // Every feature switch, pinch slot and extra is answered, so each model's sections can be seen.
                abilities = FeatureAbilities(needsReply = false, capabilities = mapOf(0x03 to 1, 0x07 to 1, 0x09 to 0, 0x0B to 1, 0x19 to 0)),
                features = mapOf(
                    Feature.EAR_TIP to FeatureState(Feature.EAR_TIP.key, 1),
                    Feature.HEAD_CONTROL to FeatureState(Feature.HEAD_CONTROL.key, 1, 1, 2),
                ),
                pinch = listOf(PinchSlot(0, 1) to 0, PinchSlot(1, 1) to 1, PinchSlot(0, 2) to 2, PinchSlot(1, 2) to 4, PinchSlot(2, 2) to 3, PinchSlot(3, 0) to 6)
                    .associate { (slot, value) -> slot to PinchSetting(slot, value, if (slot.type == 3) 5 else value) },
                equalizerExtended = true,
                restReminder = true,
                hdCall = false,
                pickupMode = 1,
                ringing = mapOf(Side.LEFT to false, Side.RIGHT to false),
            ),
            multipointEnabled = true,
            hosts = listOf(
                HostRow(index = 0, count = 2, mac = "11:22:33:44:55:66", name = "Phone", connection = 9, preferred = true, autoConnect = true),
                HostRow(index = 1, count = 2, mac = "11:22:33:44:55:77", name = "Laptop", connection = 1, preferred = false, autoConnect = true),
            ),
        ),
    )

    override val state: StateFlow<BudsState> = mutable.asStateFlow()

    override suspend fun setAnc(mode: AncMode): Result<AncState> {
        delay(applyDelayMillis)
        val next = AncState(modeCode = mode.code, level = mutable.value.anc?.level ?: 0)
        mutable.update { it.copy(anc = next, updatedAtMillis = clock()) }
        return Result.success(next)
    }

    override suspend fun setAncLevel(level: Int): Result<AncState> {
        delay(applyDelayMillis)
        val next = AncState(modeCode = AncMode.CANCELLATION.code, level = level)
        mutable.update { it.copy(anc = next, updatedAtMillis = clock()) }
        return Result.success(next)
    }

    override suspend fun refresh(): Result<Unit> {
        mutable.update { it.copy(updatedAtMillis = clock()) }
        return Result.success(Unit)
    }

    /** Takes every change as the device would: the same optimistic result the screens show. */
    override suspend fun apply(change: SettingChange): Result<Unit> {
        delay(applyDelayMillis)
        mutable.update { change.applyTo(it).copy(updatedAtMillis = clock()) }
        return Result.success(Unit)
    }

    override suspend fun ring(side: Side, ring: Boolean): Result<Unit> {
        delay(applyDelayMillis)
        mutable.update { it.copy(settings = it.settings.copy(ringing = it.settings.ringing + (side to ring)), updatedAtMillis = clock()) }
        return Result.success(Unit)
    }

    override suspend fun refreshHosts(): Result<List<HostRow>> {
        delay(applyDelayMillis)
        return Result.success(mutable.value.hosts)
    }
}
