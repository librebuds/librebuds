// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.overlay

/**
 * The connection island currently owned by the connection service, so a case-open popup can take
 * it off screen before showing itself and the two never overlap. Main thread only; the service
 * registers while it runs and unregisters when it stops, so nothing outlives it.
 */
object ConnectionIslandSlot {
    private var isOpen: () -> Boolean = { false }
    private var close: () -> Unit = {}

    fun register(isOpen: () -> Boolean, close: () -> Unit) {
        this.isOpen = isOpen
        this.close = close
    }

    fun unregister() {
        isOpen = { false }
        close = {}
    }

    fun isOpen(): Boolean = isOpen.invoke()

    fun close() = close.invoke()
}
