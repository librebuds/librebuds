// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.service

import io.github.librebuds.state.LinkState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceStopTrackerTest {
    @Test
    fun failedConnectStopsWhenNoConnectIsActive() {
        val tracker = ServiceStopTracker()
        assertFalse(tracker.onState(LinkState.DISCONNECTED, connectActive = true))
        assertFalse(tracker.onState(LinkState.CONNECTING, connectActive = true))
        assertTrue(tracker.onState(LinkState.DISCONNECTED, connectActive = false))
    }

    @Test
    fun failedConnectWaitsForActiveConnectThenStops() {
        // Double START: the first attempt fails while the queued second attempt is still running.
        val tracker = ServiceStopTracker()
        tracker.onState(LinkState.CONNECTING, connectActive = true)
        assertFalse(tracker.onState(LinkState.DISCONNECTED, connectActive = true))
        assertTrue(tracker.onConnectFinished(LinkState.DISCONNECTED))
    }

    @Test
    fun pendingStopIsCancelledWhenTheLaterConnectSucceeds() {
        val tracker = ServiceStopTracker()
        tracker.onState(LinkState.CONNECTING, connectActive = true)
        tracker.onState(LinkState.DISCONNECTED, connectActive = true)
        assertFalse(tracker.onState(LinkState.CONNECTING, connectActive = true))
        assertFalse(tracker.onState(LinkState.CONNECTED, connectActive = true))
        assertFalse(tracker.onConnectFinished(LinkState.CONNECTED))
    }

    @Test
    fun connectFinishingWithoutPendingStopKeepsService() {
        val tracker = ServiceStopTracker()
        tracker.onState(LinkState.CONNECTED, connectActive = false)
        assertFalse(tracker.onConnectFinished(LinkState.CONNECTED))
    }

    @Test
    fun takenOverSeenFirstThenDisconnectedStops() {
        // A START while TAKEN_OVER launches no connect; losing the earbuds later must still stop.
        val tracker = ServiceStopTracker()
        assertFalse(tracker.onState(LinkState.TAKEN_OVER, connectActive = false))
        assertTrue(tracker.onState(LinkState.DISCONNECTED, connectActive = false))
    }
}
