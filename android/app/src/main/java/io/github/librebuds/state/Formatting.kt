// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.state

import io.github.librebuds.protocol.command.BatteryState

/** One-line battery text for the tile subtitle, notification and widgets. */
fun batterySummary(battery: BatteryState?): String {
    fun level(value: Int?) = value?.let { "$it%" } ?: "--"
    val text = "L ${level(battery?.left)} · R ${level(battery?.right)} · Case ${level(battery?.case)}"
    return if (battery?.caseCharging == true) "$text (charging)" else text
}

/** [batterySummary] with each label kept on the same line as its level, for text that wraps. */
fun batterySummaryNoBreak(battery: BatteryState?): String =
    batterySummary(battery).replace(Regex("\\b(L|R|Case) "), "$1 ").replace(" (charging)", " (charging)")
