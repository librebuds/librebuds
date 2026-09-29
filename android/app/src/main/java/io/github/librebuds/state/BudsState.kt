// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.BatteryState
import kotlinx.coroutines.flow.StateFlow

enum class LinkState { DISCONNECTED, CONNECTING, CONNECTED, TAKEN_OVER }

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
}
