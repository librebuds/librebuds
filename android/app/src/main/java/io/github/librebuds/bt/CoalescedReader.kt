// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.bt

import java.util.concurrent.atomic.AtomicInteger

/**
 * Reads something slow in the [background] and hands it to [deliver] on the [main] thread, once per
 * burst of [request]s: a request made while a read is queued joins it. A request that arrives after
 * the queued read already started gets one more read, so the last delivery is never older than the
 * last request. [request] and [deliver] run on the main thread; [read] on the background one.
 */
class CoalescedReader<T>(
    private val background: (Runnable) -> Unit,
    private val main: (Runnable) -> Unit,
    private val read: () -> T,
    private val deliver: (T) -> Unit,
) {
    private val requests = AtomicInteger()

    // Main thread only.
    private var queued = false

    fun request() {
        requests.incrementAndGet()
        if (queued) return
        queued = true
        launch()
    }

    private fun launch() {
        background(
            Runnable {
                val seen = requests.get()
                val value = read()
                main(
                    Runnable {
                        deliver(value)
                        if (requests.get() != seen) launch() else queued = false
                    },
                )
            },
        )
    }
}
