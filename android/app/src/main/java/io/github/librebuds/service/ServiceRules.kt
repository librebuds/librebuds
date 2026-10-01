// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.service

import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkState

/**
 * Whether the background connection service should be running: Bluetooth is on, the app may use it,
 * and at least one pair of FreeBuds is paired. The service then waits for any of them to connect to
 * the phone; it does not stop when the earbuds go away, only when one of these turns false.
 */
fun serviceShouldRun(bluetoothOn: Boolean, connectGranted: Boolean, bondedFreeBuds: Int): Boolean =
    bluetoothOn && connectGranted && bondedFreeBuds > 0

/**
 * Whether a request to connect [address] should launch a connect. Not while a connect for the same earbuds
 * is running or done, and not while they are TAKEN_OVER: only an explicit takeOver() takes them back.
 */
fun shouldLaunchConnect(state: BudsState, address: String): Boolean {
    if (!address.equals(state.address, ignoreCase = true)) return true
    return state.link == LinkState.DISCONNECTED
}

/**
 * Decides which of the FreeBuds connected to the phone the controller should hold, from the Bluetooth
 * events the service sees. Pure bookkeeping, so the rules are unit tested without Bluetooth:
 *
 * - The controller keeps its pair while that pair is still connected to the phone and the link is
 *   CONNECTING, CONNECTED or TAKEN_OVER. A pair held by another device is never reclaimed here, and
 *   never left either while it stays connected, because switching away would forget the take-over.
 * - Otherwise the most recently connected pair wins; addresses without a recorded time come last.
 * - A connect that failed is not retried on every broadcast: the address counts as attempted until
 *   its link comes up again ([onLinkUp]: a fresh ACL, A2DP or headset connection).
 * - A pair once seen TAKEN_OVER stays excluded until it leaves the phone ([onGone]) or the user takes
 *   it back, also after the controller moved on to another pair (the user may switch explicitly).
 */
class AutoConnectPlanner {
    private val attempted = mutableSetOf<String>()
    private val heldElsewhere = mutableSetOf<String>()

    /** The link of [address] to the phone came up (again): a connect may be tried once more. */
    fun onLinkUp(address: String) {
        attempted -= address.uppercase()
    }

    /** [address] left the phone: forget everything about it. */
    fun onGone(address: String) {
        attempted -= address.uppercase()
        heldElsewhere -= address.uppercase()
    }

    /**
     * Follows the controller state, so a take-over is remembered even after the controller moved on,
     * and forgotten once the controller holds those earbuds again (the user took them back).
     */
    fun onState(state: BudsState) {
        val address = state.address?.uppercase() ?: return
        when (state.link) {
            LinkState.TAKEN_OVER -> heldElsewhere += address
            LinkState.CONNECTED -> heldElsewhere -= address
            else -> Unit
        }
    }

    /** Records that a connect to [address] was launched. */
    fun onLaunched(address: String) {
        attempted += address.uppercase()
    }

    /** Whether [address] is held by another device as far as this planner knows. */
    fun isHeldElsewhere(address: String): Boolean = address.uppercase() in heldElsewhere

    /**
     * The address to connect now, or null to leave the controller as it is. [connected] are the FreeBuds
     * connected to the phone (any case), [lastConnectedAt] their last connect times by uppercase address.
     */
    fun target(connected: Collection<String>, lastConnectedAt: Map<String, Long>, state: BudsState): String? {
        val up = connected.mapTo(linkedSetOf()) { it.uppercase() }
        if (up.isEmpty()) return null
        val current = state.address?.uppercase()
        if (current != null && current in up && state.link != LinkState.DISCONNECTED) return null
        val candidates = up - attempted - heldElsewhere
        return candidates.maxWithOrNull(
            compareBy<String> { lastConnectedAt[it] ?: Long.MIN_VALUE }
                .thenBy { it == current }
                .thenByDescending { it }
        )
    }
}
