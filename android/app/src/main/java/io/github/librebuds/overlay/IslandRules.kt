// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.overlay

import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.state.LinkState
import io.github.librebuds.ui.model.Battery
import io.github.librebuds.ui.model.BatteryComponent
import io.github.librebuds.ui.model.BatteryStatus

/**
 * Whether the link moving from [previous] to [current] should show the connection island.
 *
 * Only a real transition into CONNECTED counts. [previous] is null for the first state the service
 * observes; a CONNECTED value there means the earbuds were already up before this start (for example
 * a second start request), which is not a fresh connection. While the case-open popup for these
 * earbuds is on screen ([popupShowing]) it already shows the connection, so the island stays away.
 */
fun islandShouldShow(
    previous: LinkState?,
    current: LinkState,
    enabled: Boolean,
    canDrawOverlays: Boolean,
    popupShowing: Boolean,
): Boolean =
    enabled && canDrawOverlays && !popupShowing &&
        current == LinkState.CONNECTED && previous != null && previous != LinkState.CONNECTED

/** The lower of the two earbud levels, or whichever one is known; 0 when neither is. */
fun islandBatteryLevel(battery: BatteryState?): Int =
    listOfNotNull(battery?.left, battery?.right).minOrNull() ?: 0

/** One entry of the case-open island's battery line: a [BatteryComponent] with its level. */
data class IslandBatteryPart(val component: Int, val level: Int, val charging: Boolean)

/** Left, right and case, in that order, leaving out the ones the earbuds did not report. */
fun islandBatteryParts(batteries: List<Battery>): List<IslandBatteryPart> =
    listOf(BatteryComponent.LEFT, BatteryComponent.RIGHT, BatteryComponent.CASE).mapNotNull { component ->
        batteries.find { it.component == component && it.status != BatteryStatus.DISCONNECTED }
            ?.let { IslandBatteryPart(component, it.level, it.status == BatteryStatus.CHARGING) }
    }
