// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {
    private val buds = Route.Device("AA:BB:CC:DD:EE:FF", "HUAWEI FreeBuds 6")
    private val other = Route.Device("11:22:33:44:55:66", "HUAWEI FreeBuds 5")

    @Test
    fun startsOnTheListOnceOnboardingIsDone() {
        assertEquals(listOf(Route.Home), BackStack.initial(onboardingDone = true).entries)
        assertEquals(listOf(Route.Onboarding), BackStack.initial(onboardingDone = false).entries)
    }

    @Test
    fun backOnTheListLeavesTheApp() {
        assertNull(BackStack.initial(true).back())
    }

    @Test
    fun deviceScreenBackReturnsToTheList() {
        val list = BackStack.initial(true).openStart(null)
        val device = list.push(buds)
        assertEquals(listOf(Route.Home, buds), device.entries)
        assertTrue("Back is handled in the app, not by leaving it", device.canGoBack)
        assertEquals(listOf(Route.Home), device.back()!!.entries)
        assertNull("Back on the list leaves the app", device.back()!!.back())
    }

    @Test
    fun launchedIntoADeviceBackStillShowsTheList() {
        val launched = BackStack.initial(onboardingDone = true, launchDevice = buds)
        assertEquals(buds, launched.top)
        assertFalse("no further start jump", launched.startPending)
        assertEquals(listOf(Route.Home), launched.back()!!.entries)
        assertNull(launched.back()!!.back())
    }

    @Test
    fun theStartJumpOpensThePairOnTopOfTheListOnce() {
        val pending = BackStack.initial(true)
        assertTrue(pending.startPending)
        val opened = pending.openStart(buds)
        assertEquals(listOf(Route.Home, buds), opened.entries)
        assertFalse(opened.startPending)
        // Used up: a later call (detection read again) does not move the person.
        assertEquals(opened, opened.back()!!.openStart(other).push(buds))
        assertEquals(listOf(Route.Home), opened.back()!!.openStart(other).entries)
    }

    @Test
    fun theStartJumpWithNoPairStaysOnTheList() {
        val stayed = BackStack.initial(true).openStart(null)
        assertEquals(listOf(Route.Home), stayed.entries)
        assertFalse(stayed.startPending)
        assertEquals(stayed, stayed.openStart(buds))
    }

    @Test
    fun navigatingBeforeDetectionSettledUsesUpTheStartJump() {
        val settings = BackStack.initial(true).push(Route.Settings)
        assertFalse(settings.startPending)
        assertEquals(listOf(Route.Home), settings.back()!!.openStart(buds).entries)
        val tapped = BackStack.initial(true).push(other)
        assertEquals(listOf(Route.Home, other), tapped.openStart(buds).entries)
    }

    @Test
    fun theStartJumpWaitsForOnboarding() {
        val onboarding = BackStack.initial(onboardingDone = false, launchDevice = buds)
        assertEquals(listOf(Route.Onboarding), onboarding.entries)
        assertEquals(onboarding, onboarding.openStart(buds))
        val done = onboarding.finishOnboarding()
        assertEquals(listOf(Route.Home), done.entries)
        assertNull("Back does not return to onboarding", done.back())
        assertEquals(listOf(Route.Home, buds), done.openStart(buds).entries)
    }

    @Test
    fun settingsAndMultipointReturnToTheScreenBelow() {
        val device = BackStack.initial(true, buds)
        assertEquals(device, device.push(Route.Settings).back())
        assertEquals(device, device.push(Route.Multipoint).back())
        assertEquals(device.push(Route.Multipoint), device.push(Route.Multipoint).push(Route.Settings).back())
        val list = BackStack.initial(true).openStart(null)
        assertEquals(list, list.push(Route.Settings).back())
    }

    @Test
    fun theOpenDeviceIsTheTopmostDeviceScreen() {
        val list = BackStack.initial(true).openStart(null)
        assertNull(list.device)
        assertEquals(buds, list.push(buds).push(Route.Settings).device)
    }

    @Test
    fun aGonePairsScreenIsDroppedWithEverythingAboveIt() {
        val stack = BackStack.initial(true, buds).push(Route.Multipoint).push(Route.Settings)
        assertEquals(listOf(Route.Home), stack.without(buds.address.lowercase()).entries)
        assertEquals(stack, stack.without(other.address))
    }

    @Test
    fun pushingTheSameScreenTwiceKeepsOneEntry() {
        val stack = BackStack.initial(true).openStart(null).push(Route.Settings).push(Route.Settings)
        assertEquals(listOf(Route.Home, Route.Settings), stack.entries)
    }

    @Test
    fun topKeysDifferPerEntryAndPerPair() {
        val root = BackStack.initial(true).openStart(null)
        val device = root.push(buds)
        val settings = device.push(Route.Settings)
        assertEquals(device.topKey(), settings.back()!!.topKey())
        assertEquals(3, setOf(root.topKey(), device.topKey(), settings.topKey()).size)
        assertTrue(root.push(buds).topKey() != root.push(other).topKey())
    }

    @Test
    fun encodesAndDecodes() {
        val stack = BackStack.initial(true, buds).push(Route.Multipoint).push(Route.Settings)
        assertEquals(stack, BackStack.decode(stack.encode()))
        assertEquals(BackStack.initial(true), BackStack.decode(BackStack.initial(true).encode()))
        assertEquals(BackStack.initial(false), BackStack.decode(BackStack.initial(false).encode()))
        // A name with the separator survives; only the first one splits.
        val piped = BackStack.initial(true, Route.Device("AA:BB:CC:DD:EE:FF", "Buds | Pro"))
        assertEquals(piped, BackStack.decode(piped.encode()))
        assertNull(BackStack.decode(listOf("start:done", "home", "nowhere")))
        // A stack saved by the version with the device screen as the root falls back to a fresh start.
        assertNull(BackStack.decode(listOf("buds", "settings")))
        assertNull(BackStack.decode(listOf("start:done")))
        assertNull(BackStack.decode(emptyList()))
    }
}
