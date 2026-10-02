// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

/**
 * A screen of the app. [Home] is the root: the list of known pairs, or what to do when no FreeBuds
 * are paired. [Device] is one pair's screen; it carries the name so the title shows before detection
 * has read the pair again (after a restart or when the app is opened straight into it).
 */
sealed interface Route {
    data object Onboarding : Route
    data object Home : Route
    data class Device(val address: String, val name: String) : Route
    data object Settings : Route
    data object Multipoint : Route
}

/**
 * The back stack, as an immutable value. [Route.Home] is the root once onboarding is done; Back pops
 * one screen, and Back on the root leaves the app ([back] returns null). A device screen always has
 * the list below it, also when the app opened straight into it, so Back from there shows the list.
 * [startPending] holds the one-time jump to the start pair (see [startPair]) until detection settled.
 */
data class BackStack(val entries: List<Route>, val startPending: Boolean = false) {
    init {
        require(entries.isNotEmpty()) { "The back stack is never empty" }
    }

    val top: Route get() = entries.last()

    val canGoBack: Boolean get() = entries.size > 1

    /** The pair of the topmost device screen in the stack, if any (Settings may be open above it). */
    val device: Route.Device? get() = entries.lastOrNull { it is Route.Device } as Route.Device?

    /** Any navigation by the person uses up the pending start jump, so it never moves them later. */
    fun push(route: Route): BackStack = if (top == route) copy(startPending = false) else BackStack(entries + route, startPending = false)

    /** The stack after Back, or null when Back should leave the app. */
    fun back(): BackStack? = if (canGoBack) BackStack(entries.dropLast(1), startPending = false) else null

    /** Onboarding is replaced by the list, so Back does not return to it; the start jump applies once. */
    fun finishOnboarding(): BackStack = BackStack(listOf(Route.Home), startPending = true)

    /**
     * Applies the start jump once: while it is pending and the list is the only screen, [start] (the
     * pair to open, or null to stay on the list) opens on top of the list. Waits while onboarding
     * shows. Called when detection settled, or earlier when the start pair is already certain.
     */
    fun openStart(start: Route.Device?): BackStack {
        if (!startPending || top == Route.Onboarding) return this
        val opened = if (entries == listOf(Route.Home) && start != null) entries + start else entries
        return BackStack(opened, startPending = false)
    }

    /** Drops the device screen of [address] and everything above it, e.g. once the pair was unpaired. */
    fun without(address: String): BackStack {
        val index = entries.indexOfFirst { it is Route.Device && it.address.equals(address, ignoreCase = true) }
        return if (index <= 0) this else BackStack(entries.take(index), startPending)
    }

    /** Identifies the top entry for per-screen saved state; unique within the stack. */
    fun topKey(): String = "${entries.lastIndex}:${encodeRoute(top)}"

    /** Strings for saved instance state. */
    fun encode(): ArrayList<String> = ArrayList(listOf(if (startPending) PENDING else DONE) + entries.map(::encodeRoute))

    companion object {
        private const val PENDING = "start:pending"
        private const val DONE = "start:done"
        private const val DEVICE_PREFIX = "device:"

        /**
         * The stack on a fresh start: onboarding, or the list with [launchDevice] on top when the app
         * was opened for a known pair (notification, island). Otherwise the start jump is pending.
         */
        fun initial(onboardingDone: Boolean, launchDevice: Route.Device? = null): BackStack = when {
            !onboardingDone -> BackStack(listOf(Route.Onboarding), startPending = true)
            launchDevice != null -> BackStack(listOf(Route.Home, launchDevice))
            else -> BackStack(listOf(Route.Home), startPending = true)
        }

        /** The stack [encode] wrote, or null when the saved form is unusable. */
        fun decode(saved: List<String>?): BackStack? {
            if (saved.isNullOrEmpty()) return null
            val marker = saved.first()
            if (marker != PENDING && marker != DONE) return null
            val routes = saved.drop(1).map { decodeRoute(it) ?: return null }
            if (routes.isEmpty()) return null
            return BackStack(routes, startPending = marker == PENDING)
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
