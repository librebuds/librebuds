// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.service

import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceRulesTest {
    private val a = "AA:AA:AA:AA:AA:AA"
    private val b = "BB:BB:BB:BB:BB:BB"
    private val c = "CC:CC:CC:CC:CC:CC"

    @Test
    fun runsOnlyWithBluetoothOnPermissionAndPairedFreeBuds() {
        assertTrue(serviceShouldRun(bluetoothOn = true, connectGranted = true, bondedFreeBuds = 1))
        assertTrue(serviceShouldRun(bluetoothOn = true, connectGranted = true, bondedFreeBuds = 3))
        assertFalse(serviceShouldRun(bluetoothOn = false, connectGranted = true, bondedFreeBuds = 1))
        assertFalse(serviceShouldRun(bluetoothOn = true, connectGranted = false, bondedFreeBuds = 1))
        assertFalse(serviceShouldRun(bluetoothOn = true, connectGranted = true, bondedFreeBuds = 0))
    }

    @Test
    fun nothingConnectedToThePhoneMeansNoTarget() {
        assertNull(AutoConnectPlanner().target(emptyList(), emptyMap(), BudsState()))
    }

    @Test
    fun aSingleConnectedPairIsTheTarget() {
        assertEquals(a, AutoConnectPlanner().target(listOf(a.lowercase()), emptyMap(), BudsState()))
    }

    @Test
    fun theMostRecentlyConnectedPairWins() {
        val history = mapOf(a to 100L, b to 300L, c to 200L)
        assertEquals(b, AutoConnectPlanner().target(listOf(a, b, c), history, BudsState()))
    }

    @Test
    fun aPairWithoutHistoryComesAfterOneWithHistory() {
        assertEquals(b, AutoConnectPlanner().target(listOf(a, b), mapOf(b to 1L), BudsState()))
    }

    @Test
    fun withoutAnyHistoryTheControllersLastPairIsPreferred() {
        val state = BudsState(link = LinkState.DISCONNECTED, address = b)
        assertEquals(b, AutoConnectPlanner().target(listOf(a, b), emptyMap(), state))
    }

    @Test
    fun aLiveOrPendingLinkIsKeptEvenWhenANewerPairConnects() {
        val history = mapOf(a to 100L, b to 300L)
        for (link in listOf(LinkState.CONNECTED, LinkState.CONNECTING)) {
            assertNull(link.name, AutoConnectPlanner().target(listOf(a, b), history, BudsState(link = link, address = a)))
        }
    }

    @Test
    fun aTakenOverPairIsNeverReclaimedNorLeftWhileStillConnected() {
        val planner = AutoConnectPlanner()
        val takenOver = BudsState(link = LinkState.TAKEN_OVER, address = a)
        planner.onState(takenOver)
        assertNull(planner.target(listOf(a), mapOf(a to 1L), takenOver))
        // Another pair connecting does not make the controller drop the take-over either.
        assertNull(planner.target(listOf(a, b), mapOf(a to 1L, b to 2L), takenOver))
    }

    @Test
    fun aTakenOverPairStaysExcludedAfterTheControllerMovedOnUntilItLeaves() {
        val planner = AutoConnectPlanner()
        planner.onState(BudsState(link = LinkState.TAKEN_OVER, address = a))
        // The user switched to b explicitly; b then dropped.
        val onB = BudsState(link = LinkState.DISCONNECTED, address = b)
        planner.onLaunched(b)
        assertNull(planner.target(listOf(a, b), mapOf(a to 5L, b to 1L), onB))
        assertTrue(planner.isHeldElsewhere(a))

        planner.onGone(a)
        planner.onLinkUp(a)
        assertFalse(planner.isHeldElsewhere(a))
        assertEquals(a, planner.target(listOf(a), mapOf(a to 9L), onB))
    }

    @Test
    fun aFailedConnectIsNotRetriedUntilTheLinkComesUpAgain() {
        val planner = AutoConnectPlanner()
        assertEquals(a, planner.target(listOf(a), emptyMap(), BudsState()))
        planner.onLaunched(a)
        val failed = BudsState(link = LinkState.DISCONNECTED, address = a)
        assertNull("no retry on every broadcast", planner.target(listOf(a), emptyMap(), failed))

        planner.onLinkUp(a.lowercase())
        assertEquals("a fresh ACL, A2DP or headset connection allows one more try", a, planner.target(listOf(a), emptyMap(), failed))
    }

    @Test
    fun anAttemptedPairDoesNotBlockAnotherConnectedPair() {
        val planner = AutoConnectPlanner()
        planner.onLaunched(b)
        val failed = BudsState(link = LinkState.DISCONNECTED, address = b)
        assertEquals(a, planner.target(listOf(a, b), mapOf(b to 9L, a to 1L), failed))
    }

    @Test
    fun whenTheHeldPairLeavesTheOtherConnectedPairIsNext() {
        val planner = AutoConnectPlanner()
        // a was connected and dropped (its ACL went away), b is still connected to the phone.
        planner.onLaunched(a)
        planner.onGone(a)
        val dropped = BudsState(link = LinkState.DISCONNECTED, address = a)
        assertEquals(b, planner.target(listOf(b), mapOf(a to 9L, b to 1L), dropped))
    }
}
