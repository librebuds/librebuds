/*
    LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2025 LibrePods contributors
    SPDX-License-Identifier: GPL-3.0-or-later
    Modified for LibreBuds (2026): adapted to FreeBuds; see NOTICE.
*/
package io.github.librebuds.ui.model

import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.BatteryState

// Same shapes as the upstream UI expects, so the imported battery components stay unchanged.
object BatteryComponent {
    const val LEFT = 4
    const val RIGHT = 2
    const val CASE = 8
}

object BatteryStatus {
    const val CHARGING = 1
    const val NOT_CHARGING = 2
    const val DISCONNECTED = 4
}

data class Battery(val component: Int, val level: Int, val status: Int)

fun BatteryState?.toUiBatteries(): List<Battery> {
    if (this == null) return emptyList()
    return buildList {
        fun add(component: Int, level: Int?, charging: Boolean?) {
            if (level != null) {
                add(Battery(component, level, if (charging == true) BatteryStatus.CHARGING else BatteryStatus.NOT_CHARGING))
            }
        }
        add(BatteryComponent.LEFT, left, leftCharging)
        add(BatteryComponent.RIGHT, right, rightCharging)
        add(BatteryComponent.CASE, case, caseCharging)
    }
}

/** Noise-control modes FreeBuds support. Upstream's ADAPTIVE mode has no FreeBuds equivalent. */
enum class NoiseControlMode(val anc: AncMode) {
    OFF(AncMode.OFF),
    NOISE_CANCELLATION(AncMode.CANCELLATION),
    AWARENESS(AncMode.AWARENESS);

    companion object {
        fun of(state: AncState?): NoiseControlMode? = entries.firstOrNull { it.anc.code == state?.modeCode }
    }
}
