// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {
    private val buds = Route.Device("33:33:33:33:33:33", "HUAWEI FreeBuds 6")
    private val other = Route.Device("44:44:44:44:44:44", "FreeBuds Pro 4")

    @Test
    fun coldStartWithOneConnectedOpensItOverHome() {
        val stack = BackStack.initial(onboardingDone = true).autoOpen(listOf(buds))
        assertEquals(listOf(Route.Home, buds), stack.entries)

        val home = stack.back()!!
        assertEquals(listOf(Route.Home), home.entries)
        assertNull("Back at home leaves the app", home.back())
    }

    @Test
    fun coldStartWithNoneOrSeveralConnectedStaysHome() {
        assertEquals(listOf(Route.Home), BackStack.initial(true).autoOpen(emptyList()).entries)
        assertEquals(listOf(Route.Home), BackStack.initial(true).autoOpen(listOf(buds, other)).entries)
    }

    @Test
    fun autoOpenFiresOnlyOnce() {
        val stack = BackStack.initial(true).autoOpen(emptyList())
        assertFalse(stack.autoOpenPending)
        assertEquals(listOf(Route.Home), stack.autoOpen(listOf(buds)).entries)
    }

    @Test
    fun autoOpenWaitsForOnboardingThenApplies() {
        val onboarding = BackStack.initial(onboardingDone = false).autoOpen(listOf(buds))
        assertEquals(listOf(Route.Onboarding), onboarding.entries)
        assertTrue(onboarding.autoOpenPending)

        val home = onboarding.finishOnboarding()
        assertEquals(listOf(Route.Home), home.entries)
        assertEquals(listOf(Route.Home, buds), home.autoOpen(listOf(buds)).entries)
    }

    @Test
    fun autoOpenDoesNotJumpOverAScreenTheUserOpened() {
        val stack = BackStack.initial(true).push(Route.Settings).autoOpen(listOf(buds))
        assertEquals(listOf(Route.Home, Route.Settings), stack.entries)
        assertFalse(stack.autoOpenPending)
    }

    @Test
    fun settingsAndMultipointReturnToWhereTheyWereOpened() {
        val device = BackStack.initial(true).push(buds)
        assertEquals(device, device.push(Route.Settings).back())
        assertEquals(device, device.push(Route.Multipoint).back())

        val home = BackStack.initial(true)
        assertEquals(home, home.push(Route.Settings).back())
    }

    @Test
    fun pushingTheSameScreenTwiceKeepsOneEntry() {
        val stack = BackStack.initial(true).push(buds).push(buds)
        assertEquals(listOf(Route.Home, buds), stack.entries)
    }

    @Test
    fun encodesAndDecodes() {
        val stack = BackStack.initial(true).push(Route.Device("AA:BB:CC:DD:EE:FF", "Buds | renamed")).push(Route.Multipoint)
        assertEquals(stack, BackStack.decode(stack.encode()))
        val done = BackStack.initial(true).autoOpen(emptyList())
        assertEquals(done, BackStack.decode(done.encode()))
        assertNull(BackStack.decode(listOf("auto:pending", "nowhere")))
        assertNull(BackStack.decode(emptyList()))
    }
}
