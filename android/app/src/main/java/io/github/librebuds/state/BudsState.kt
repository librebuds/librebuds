// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.protocol.command.HostRow
import kotlinx.coroutines.flow.StateFlow

enum class LinkState { DISCONNECTED, CONNECTING, CONNECTED, TAKEN_OVER }

/** Why the last session ended on our side, kept until the next connect attempt. */
enum class LinkError { NO_REPLY }

data class DeviceSummary(val model: String? = null, val firmware: String? = null, val serial: String? = null)

/** Everything the UI knows about the current earbuds. Null fields mean "not reported yet". */
data class BudsState(
    val link: LinkState = LinkState.DISCONNECTED,
    val address: String? = null,
    val name: String? = null,
    val profileId: String = "generic",
    val capabilities: Set<String> = setOf("battery", "anc"),
    val battery: BatteryState? = null,
    val anc: AncState? = null,
    val device: DeviceSummary = DeviceSummary(),
    val updatedAtMillis: Long? = null,
    val lastError: LinkError? = null,
    val settings: DeviceSettings = DeviceSettings(),
    val hosts: List<HostRow> = emptyList(),
    val multipointEnabled: Boolean? = null,
    val inEar: Boolean? = null,
) {
    val isConnected: Boolean get() = link == LinkState.CONNECTED
}

/** Single source of earbud state for screens, tile, widgets and overlays. */
interface BudsRepository {
    val state: StateFlow<BudsState>

    /**
     * Requests a noise-control mode; succeeds only once the device confirmed it.
     * Implementations must tolerate cancellation: the caller may cancel after the write was sent;
     * state must still converge through later reads or reports.
     */
    suspend fun setAnc(mode: AncMode): Result<AncState>

    suspend fun refresh(): Result<Unit>

    /** Reconnects after another client took the link (spec 8). */
    suspend fun takeOver(): Result<Unit> = Result.success(Unit)

    /**
     * Applies one settings change; succeeds only once a read-back after the settle delay shows
     * the device took it. Same cancellation contract as [setAnc].
     */
    suspend fun apply(change: SettingChange): Result<Unit> = Result.failure(UnsupportedOperationException())

    /** Re-enumerates the multipoint hosts; a list that stays incomplete is returned as far as it got. */
    suspend fun refreshHosts(): Result<List<HostRow>> = Result.failure(UnsupportedOperationException())
}
