// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.service

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
