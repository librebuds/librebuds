// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.qs

import io.github.librebuds.state.BudsState
import io.github.librebuds.state.batterySummary
import io.github.librebuds.ui.model.NoiseControlMode

data class TileModel(val active: Boolean, val label: String, val subtitle: String)

/**
 * Tile text for a state. English literals kept in sync with strings.xml (`noise_cancellation`,
 * `awareness`, `not_connected`); the tile uses plain strings so this stays unit-testable.
 */
fun tileModel(state: BudsState): TileModel {
    if (!state.isConnected) return TileModel(active = false, label = "LibreBuds", subtitle = "Not connected")
    val label = when (NoiseControlMode.of(state.anc)) {
        NoiseControlMode.OFF -> "Noise control off"
        NoiseControlMode.NOISE_CANCELLATION -> "Noise cancellation"
        NoiseControlMode.AWARENESS -> "Awareness"
        null -> "LibreBuds"
    }
    return TileModel(active = true, label = label, subtitle = batterySummary(state.battery))
}
