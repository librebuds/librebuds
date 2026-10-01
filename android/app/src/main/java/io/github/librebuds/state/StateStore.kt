// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import android.content.Context
import androidx.core.content.edit
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.BatteryState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The part of [BudsState] that outlives the process: which earbuds, whether another device held
 * them, the last known battery and noise control, and the model, firmware and serial number read
 * at the last connect. Settings and hosts are session data and are read again on the next connect.
 */
@Serializable
data class PersistedState(
    val version: Int,
    val link: LinkState,
    val address: String?,
    val name: String?,
    val profileId: String,
    val battery: PersistedBattery?,
    val ancModeCode: Int?,
    val ancLevel: Int?,
    val updatedAtMillis: Long?,
    /** The phone's boot count when this was saved; null when it could not be read. */
    val bootCount: Int?,
    /** Device details from the last connect; absent in states saved by older builds. */
    val model: String? = null,
    val firmware: String? = null,
    val serial: String? = null,
) {
    @Serializable
    data class PersistedBattery(
        val overall: Int?,
        val left: Int?,
        val right: Int?,
        val case: Int?,
        val leftCharging: Boolean?,
        val rightCharging: Boolean?,
        val caseCharging: Boolean?,
    )

    fun encode(): String = json.encodeToString(serializer(), this)

    /**
     * The state to start from. No link survives a restart, so a live link comes back
     * DISCONNECTED (the values stay as last known). TAKEN_OVER is kept only within the same boot
     * ([currentBootCount] equals the saved [bootCount]) and while [audioUp] says the phone still
     * has audio to these earbuds, so only the user's take-over reconnects them. After a reboot the
     * other device's hold is no longer known, so the earbuds may connect on their own even if
     * they already play audio to the phone. A boot count that is unknown on either side leaves
     * the decision to [audioUp] alone.
     */
    fun toBudsState(audioUp: (String) -> Boolean, currentBootCount: Int?): BudsState = BudsState(
        link = if (keepsTakeOver(audioUp, currentBootCount)) LinkState.TAKEN_OVER else LinkState.DISCONNECTED,
        address = address,
        name = name,
        profileId = profileId,
        battery = battery?.let {
            BatteryState(it.overall, it.left, it.right, it.case, it.leftCharging, it.rightCharging, it.caseCharging)
        },
        anc = if (ancModeCode != null && ancLevel != null) AncState(ancModeCode, ancLevel) else null,
        updatedAtMillis = updatedAtMillis,
        device = DeviceSummary(model, firmware, serial),
    )

    private fun keepsTakeOver(audioUp: (String) -> Boolean, currentBootCount: Int?): Boolean {
        if (link != LinkState.TAKEN_OVER || address == null) return false
        if (bootCount != null && currentBootCount != null && bootCount != currentBootCount) return false
        return audioUp(address)
    }

    companion object {
        /** Bumped whenever the format changes; a stored state of another version is dropped. */
        const val VERSION = 2

        private val json = Json

        fun from(state: BudsState, bootCount: Int?) = PersistedState(
            version = VERSION,
            link = state.link,
            address = state.address,
            name = state.name,
            profileId = state.profileId,
            battery = state.battery?.let {
                PersistedBattery(it.overall, it.left, it.right, it.case, it.leftCharging, it.rightCharging, it.caseCharging)
            },
            ancModeCode = state.anc?.modeCode,
            ancLevel = state.anc?.level,
            updatedAtMillis = state.updatedAtMillis,
            bootCount = bootCount,
            model = state.device?.model,
            firmware = state.device?.firmware,
            serial = state.device?.serial,
        )

        /** Null for anything but a well-formed state of the current [VERSION]: a fresh start, never a crash. */
        fun decode(text: String?): PersistedState? {
            if (text.isNullOrEmpty()) return null
            val decoded = try {
                json.decodeFromString(serializer(), text)
            } catch (e: Exception) {
                return null
            }
            return decoded.takeIf { it.version == VERSION }
        }
    }
}

/** Keeps the last [BudsState] in the `last_state` preferences, so a restarted process shows it. */
class StateStore(context: Context) {
    private val prefs = context.getSharedPreferences("last_state", Context.MODE_PRIVATE)

    fun load(): PersistedState? = PersistedState.decode(prefs.getString(KEY, null))

    fun save(state: PersistedState) = prefs.edit { putString(KEY, state.encode()) }

    private companion object {
        const val KEY = "state"
    }
}
