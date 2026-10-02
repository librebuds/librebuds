// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.bt

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * One background thread for the blocking work around Bluetooth broadcasts and beacons: binder calls
 * into the Bluetooth service (paired devices, names, profile proxies' connected devices) and the small
 * preference reads that go with them. Serial, so work queued in order runs in order; whatever the
 * results feed (controller, planner, popup) is posted back to the main thread with [main].
 */
object BtWorker {
    private const val TAG = "BtWorker"

    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "librebuds-bt").apply { isDaemon = true }
    }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** Runs [block] on the worker thread; an exception is logged, never thrown into the executor. */
    fun execute(block: () -> Unit) {
        executor.execute { guarded(block) }
    }

    /** Runs [block] on the worker thread after [delayMillis]. */
    fun schedule(delayMillis: Long, block: () -> Unit) {
        executor.schedule({ guarded(block) }, delayMillis, TimeUnit.MILLISECONDS)
    }

    /** Runs [block] on the main thread. */
    fun main(block: () -> Unit) {
        mainHandler.post(block)
    }

    private fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Background Bluetooth work failed", e)
        }
    }
}
