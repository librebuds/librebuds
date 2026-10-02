// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.bt

/**
 * Keeps one proxy per Bluetooth profile in [profiles] for as long as it is wanted, instead of binding and
 * closing a proxy for every question (each bind and close is a service bind on the main thread, and the
 * system logs every one). [bind] asks for a proxy (`getProfileProxy`; true when the request was sent)
 * and the answer comes back through [onConnected]; [close] gives a proxy back (`closeProfileProxy`).
 *
 * [acquire] is idempotent and may follow a [release]. A proxy that arrives after a release, or for a
 * profile not asked for, is closed at once. [onDisconnected] (Bluetooth off) only drops the proxy: the
 * system hands the same listener a new one when Bluetooth is back, so nothing is bound again here.
 * Thread-safe: the answers arrive on the main thread, [proxy] is read from the background.
 */
class ProfileProxyHolder<P : Any>(
    private val profiles: Set<Int>,
    private val bind: (profile: Int) -> Boolean,
    private val close: (profile: Int, proxy: P) -> Unit,
) {
    private var wanted = false
    private val requested = mutableSetOf<Int>()
    private val proxies = mutableMapOf<Int, P>()

    /** Asks for every profile not asked for yet; true when all requests are out. */
    @Synchronized
    fun acquire(): Boolean {
        wanted = true
        for (profile in profiles - requested) {
            if (bind(profile)) requested += profile
        }
        return requested.containsAll(profiles)
    }

    /** Closes every proxy held; later answers to earlier requests are closed when they arrive. */
    @Synchronized
    fun release() {
        wanted = false
        proxies.forEach { (profile, proxy) -> close(profile, proxy) }
        proxies.clear()
        requested.clear()
    }

    /** A proxy arrived; false when it was not wanted and has been closed. */
    @Synchronized
    fun onConnected(profile: Int, proxy: P): Boolean {
        if (!wanted || profile !in requested) {
            close(profile, proxy)
            return false
        }
        val previous = proxies.put(profile, proxy)
        if (previous != null && previous !== proxy) close(profile, previous)
        return true
    }

    @Synchronized
    fun onDisconnected(profile: Int) {
        proxies.remove(profile)
    }

    @Synchronized
    fun proxy(profile: Int): P? = proxies[profile]

    /** Whether every profile has a proxy right now. */
    @Synchronized
    fun complete(): Boolean = proxies.keys.containsAll(profiles)
}
