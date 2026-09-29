// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.overlay

import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.state.LinkState

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
