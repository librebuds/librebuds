// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.model

/** The three things the battery view shows, each with its own picture, ring and percentage. */
enum class BatteryPart(val component: Int) {
    LEFT(BatteryComponent.LEFT),
    RIGHT(BatteryComponent.RIGHT),
    CASE(BatteryComponent.CASE),
}

/**
 * One slot of the battery view. [level] is null when the earbuds did not report a usable level for
 * this part (not in the report, disconnected, or out of 0..100): the view then dims the part and
 * shows a dash instead of a percentage. A part without a level is never shown as charging.
 */
data class BatteryPartUi(val part: BatteryPart, val level: Int?, val charging: Boolean) {
    val missing: Boolean get() = level == null
}

/**
 * Left, right and case, always in that order and always all three, so the layout does not jump
 * when one bud drops out. Left and right are never merged into one ring, even with equal levels.
 */
fun batteryParts(batteries: List<Battery>): List<BatteryPartUi> = BatteryPart.entries.map { part ->
    val battery = batteries.firstOrNull { it.component == part.component }
        ?.takeIf { it.status != BatteryStatus.DISCONNECTED && it.level in 0..100 }
    BatteryPartUi(part, battery?.level, battery?.status == BatteryStatus.CHARGING)
}
