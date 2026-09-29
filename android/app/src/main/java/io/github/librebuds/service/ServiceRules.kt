// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.service

import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkState

/**
 * Whether the connection service should stop once the link moved from [previous] to [current].
 *
 * [previous] is null for the first state the service observes, which is whatever the controller
 * held before this start (usually DISCONNECTED) and must not stop the service. The service stops
 * when the link drops after it was up ([hadConnected]) or when a connect attempt ends DISCONNECTED.
 * TAKEN_OVER keeps the service: another device holds the earbuds, but they are still around.
 */
fun serviceShouldStop(previous: LinkState?, current: LinkState, hadConnected: Boolean): Boolean =
    current == LinkState.DISCONNECTED && (hadConnected || previous == LinkState.CONNECTING)

/**
 * Whether a START for [address] should launch a connect. Not while a connect for the same earbuds
 * is running or done, and not while they are TAKEN_OVER: only an explicit takeOver() takes them back.
 */
fun shouldLaunchConnect(state: BudsState, address: String): Boolean {
    if (!address.equals(state.address, ignoreCase = true)) return true
    return state.link == LinkState.DISCONNECTED
}

/**
 * Applies [serviceShouldStop] to the stream of link states the service observes. A stop that
 * arrives while a connect launched by the service is still running (a double START, where the
 * first attempt fails before the queued second one runs) is deferred until that connect finishes.
 * TAKEN_OVER counts as having connected, so losing taken-over earbuds also stops the service.
 */
class ServiceStopTracker {
    private var previous: LinkState? = null
    private var hadConnected = false
    private var stopPending = false

    /** Records [link]; true when the service should stop now. */
    fun onState(link: LinkState, connectActive: Boolean): Boolean {
        if (link == LinkState.CONNECTED || link == LinkState.TAKEN_OVER) hadConnected = true
        val stop = serviceShouldStop(previous, link, hadConnected)
        previous = link
        stopPending = stop && connectActive
        return stop && !connectActive
    }

    /** Called once the service's connect finished with the link at [link]; true when a deferred stop applies now. */
    fun onConnectFinished(link: LinkState): Boolean {
        val stop = stopPending && link == LinkState.DISCONNECTED
        stopPending = false
        return stop
    }
}
