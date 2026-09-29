// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.BatteryState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory earbuds used until Bluetooth is wired in (M2b) and for UI work without hardware. */
class DemoBudsRepository(
    private val clock: () -> Long = System::currentTimeMillis,
    private val applyDelayMillis: Long = 800,
) : BudsRepository {
    private val mutable = MutableStateFlow(
        BudsState(
            link = LinkState.CONNECTED,
            address = "00:00:00:00:00:00",
            name = "Demo earbuds",
            profileId = "freebuds-6",
            capabilities = setOf("battery", "anc"),
            battery = BatteryState(90, 100, 85, 60, false, false, true),
            anc = AncState(modeCode = AncMode.OFF.code, level = 3),
            device = DeviceSummary(model = "Demo earbuds", firmware = "1.0.0", serial = "DEMO0001"),
            updatedAtMillis = clock(),
        ),
    )

    override val state: StateFlow<BudsState> = mutable.asStateFlow()

    override suspend fun setAnc(mode: AncMode): Result<AncState> {
        delay(applyDelayMillis)
        val next = AncState(modeCode = mode.code, level = mutable.value.anc?.level ?: 0)
        mutable.update { it.copy(anc = next, updatedAtMillis = clock()) }
        return Result.success(next)
    }

    override suspend fun refresh(): Result<Unit> {
        mutable.update { it.copy(updatedAtMillis = clock()) }
        return Result.success(Unit)
    }
}
