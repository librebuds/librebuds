// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

/**
 * A screen of the app. [Buds] is the root: the device screen of the chosen pair (see [chooseStartPair]),
 * or what to do when no FreeBuds are paired. Which pair it shows is screen state, not part of the route,
 * since it is only known once detection ran and changes through the pair switcher.
 */
sealed interface Route {
    data object Onboarding : Route
    data object Buds : Route
    data object Settings : Route
    data object Multipoint : Route
}

/**
 * The back stack, as an immutable value. [Route.Buds] is the root once onboarding is done; Back pops
 * one screen, and Back on the root leaves the app ([back] returns null).
 */
data class BackStack(val entries: List<Route>) {
    init {
        require(entries.isNotEmpty()) { "The back stack is never empty" }
    }

    val top: Route get() = entries.last()

    val canGoBack: Boolean get() = entries.size > 1

    fun push(route: Route): BackStack = if (top == route) this else copy(entries = entries + route)

    /** The stack after Back, or null when Back should leave the app. */
    fun back(): BackStack? = if (canGoBack) copy(entries = entries.dropLast(1)) else null

    /** Onboarding is replaced by the root, so Back from there does not return to it. */
    fun finishOnboarding(): BackStack = copy(entries = listOf(Route.Buds))

    /** Identifies the top entry for per-screen saved state; unique within the stack. */
    fun topKey(): String = "${entries.lastIndex}:${encodeRoute(top)}"

    /** Strings for saved instance state. */
    fun encode(): ArrayList<String> = ArrayList(entries.map(::encodeRoute))

    companion object {
        fun initial(onboardingDone: Boolean): BackStack = BackStack(listOf(if (onboardingDone) Route.Buds else Route.Onboarding))

        /** The stack [encode] wrote, or null when the saved form is unusable. */
        fun decode(saved: List<String>?): BackStack? {
            if (saved.isNullOrEmpty()) return null
            return BackStack(saved.map { decodeRoute(it) ?: return null })
        }

        private fun encodeRoute(route: Route): String = when (route) {
            Route.Onboarding -> "onboarding"
            Route.Buds -> "buds"
            Route.Settings -> "settings"
            Route.Multipoint -> "multipoint"
        }

        private fun decodeRoute(value: String): Route? = when (value) {
            "onboarding" -> Route.Onboarding
            "buds" -> Route.Buds
            "settings" -> Route.Settings
            "multipoint" -> Route.Multipoint
            else -> null
        }
    }
}
