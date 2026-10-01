// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

/** A screen of the app. [Device] carries the earbuds it is about, so the title shows before any state arrives. */
sealed interface Route {
    data object Onboarding : Route
    data object Home : Route
    data class Device(val address: String, val name: String) : Route
    data object Settings : Route
    data object Multipoint : Route
}

/**
 * The back stack, as an immutable value. [Route.Home] is the root once onboarding is done; Back pops
 * one screen, and Back on the root leaves the app ([back] returns null). [autoOpenPending] holds the
 * one-time cold-start jump to the connected earbuds until detection settled.
 */
data class BackStack(val entries: List<Route>, val autoOpenPending: Boolean = true) {
    init {
        require(entries.isNotEmpty()) { "The back stack is never empty" }
    }

    val top: Route get() = entries.last()

    val canGoBack: Boolean get() = entries.size > 1

    fun push(route: Route): BackStack = if (top == route) this else copy(entries = entries + route)

    /** The stack after Back, or null when Back should leave the app. */
    fun back(): BackStack? = if (canGoBack) copy(entries = entries.dropLast(1)) else null

    /** Onboarding is replaced by home, so Back from home does not return to it. */
    fun finishOnboarding(): BackStack = copy(entries = listOf(Route.Home))

    /**
     * Applies the cold-start rule once detection settled: with exactly one pair of earbuds connected
     * and nothing opened yet, their screen opens on top of home. Waits while onboarding shows; any
     * other state uses up the jump, so it never fires later in the session.
     */
    fun autoOpen(connected: List<Route.Device>): BackStack {
        if (!autoOpenPending || top == Route.Onboarding) return this
        val opened = if (entries == listOf(Route.Home) && connected.size == 1) entries + connected.single() else entries
        return BackStack(opened, autoOpenPending = false)
    }

    /** Identifies the top entry for per-screen saved state; unique within the stack. */
    fun topKey(): String = "${entries.lastIndex}:${encodeRoute(top)}"

    /** Strings for saved instance state. */
    fun encode(): ArrayList<String> = ArrayList(listOf(if (autoOpenPending) PENDING else DONE) + entries.map(::encodeRoute))

    companion object {
        private const val PENDING = "auto:pending"
        private const val DONE = "auto:done"
        private const val DEVICE_PREFIX = "device:"

        fun initial(onboardingDone: Boolean): BackStack = BackStack(listOf(if (onboardingDone) Route.Home else Route.Onboarding))

        /** The stack [encode] wrote, or null when the saved form is unusable. */
        fun decode(saved: List<String>?): BackStack? {
            if (saved.isNullOrEmpty()) return null
            val routes = saved.drop(1).map { decodeRoute(it) ?: return null }
            if (routes.isEmpty()) return null
            return BackStack(routes, autoOpenPending = saved.first() == PENDING)
        }

        private fun encodeRoute(route: Route): String = when (route) {
            Route.Onboarding -> "onboarding"
            Route.Home -> "home"
            Route.Settings -> "settings"
            Route.Multipoint -> "multipoint"
            is Route.Device -> "$DEVICE_PREFIX${route.address}|${route.name}"
        }

        private fun decodeRoute(value: String): Route? = when {
            value == "onboarding" -> Route.Onboarding
            value == "home" -> Route.Home
            value == "settings" -> Route.Settings
            value == "multipoint" -> Route.Multipoint
            value.startsWith(DEVICE_PREFIX) -> value.removePrefix(DEVICE_PREFIX).split('|', limit = 2)
                .takeIf { it.size == 2 }?.let { (address, name) -> Route.Device(address, name) }
            else -> null
        }
    }
}
