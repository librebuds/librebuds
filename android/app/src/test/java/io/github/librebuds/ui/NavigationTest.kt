// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {
    @Test
    fun startsOnTheBudsScreenOnceOnboardingIsDone() {
        assertEquals(listOf(Route.Buds), BackStack.initial(onboardingDone = true).entries)
        assertEquals(listOf(Route.Onboarding), BackStack.initial(onboardingDone = false).entries)
    }

    @Test
    fun backOnTheBudsScreenLeavesTheApp() {
        assertNull(BackStack.initial(true).back())
    }

    @Test
    fun finishingOnboardingReplacesItWithTheRoot() {
        val stack = BackStack.initial(onboardingDone = false).finishOnboarding()
        assertEquals(listOf(Route.Buds), stack.entries)
        assertNull("Back does not return to onboarding", stack.back())
    }

    @Test
    fun settingsAndMultipointReturnToTheBudsScreen() {
        val root = BackStack.initial(true)
        assertEquals(root, root.push(Route.Settings).back())
        assertEquals(root, root.push(Route.Multipoint).back())
        assertEquals(root.push(Route.Multipoint), root.push(Route.Multipoint).push(Route.Settings).back())
    }

    @Test
    fun pushingTheSameScreenTwiceKeepsOneEntry() {
        val stack = BackStack.initial(true).push(Route.Settings).push(Route.Settings)
        assertEquals(listOf(Route.Buds, Route.Settings), stack.entries)
    }

    @Test
    fun topKeysDifferPerEntry() {
        val root = BackStack.initial(true)
        val multipoint = root.push(Route.Multipoint)
        val settings = multipoint.push(Route.Settings)
        assertEquals(multipoint.topKey(), settings.back()!!.topKey())
        assertTrue(setOf(root.topKey(), multipoint.topKey(), settings.topKey()).size == 3)
    }

    @Test
    fun encodesAndDecodes() {
        val stack = BackStack.initial(true).push(Route.Multipoint).push(Route.Settings)
        assertEquals(stack, BackStack.decode(stack.encode()))
        assertEquals(BackStack.initial(false), BackStack.decode(BackStack.initial(false).encode()))
        assertNull(BackStack.decode(listOf("buds", "nowhere")))
        // A stack saved by the version with the home list is unusable and falls back to a fresh start.
        assertNull(BackStack.decode(listOf("auto:pending", "home")))
        assertNull(BackStack.decode(emptyList()))
    }
}
