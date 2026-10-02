// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The A2DP and headset profile proxies of this process, bound once ([acquire]) and kept until the
 * connection service ends ([release]), and the reads of their connected devices into [AudioConnections].
 *
 * Earlier every question bound two fresh proxies and closed them again on the main thread; a case
 * opening brings a dozen connection broadcasts, so that meant a dozen bind/close rounds in a second.
 * Now [refresh] only reads the held proxies, on [BtWorker], once per burst of requests, and then runs
 * the [addListener] listeners on the main thread. Everything here is called on the main thread.
 */
@SuppressLint("StaticFieldLeak") // Only the application context is kept.
object AudioProfiles {
    private const val TAG = "AudioProfiles"

    /** Broadcasts of one change come within ~50 ms of each other; one read covers them all. */
    private const val READ_DELAY_MILLIS = 100L

    private val PROFILES = setOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)

    private var context: Context? = null
    private var adapter: BluetoothAdapter? = null
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    // Main thread only.
    private val whenComplete = mutableListOf<() -> Unit>()

    private val holder = ProfileProxyHolder<BluetoothProfile>(
        profiles = PROFILES,
        bind = { profile -> bind(profile) },
        close = { profile, proxy -> adapter?.closeProfileProxy(profile, proxy) },
    )

    private val reader = CoalescedReader(
        background = { BtWorker.schedule(READ_DELAY_MILLIS) { it.run() } },
        main = { BtWorker.main { it.run() } },
        read = ::readConnected,
        deliver = ::deliver,
    )

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (holder.onConnected(profile, proxy)) reader.request()
        }

        override fun onServiceDisconnected(profile: Int) = holder.onDisconnected(profile)
    }

    /**
     * Binds the proxies unless they are bound already; without the Bluetooth permission or an adapter
     * it does nothing and a later call tries again.
     */
    fun acquire(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        val app = context.applicationContext
        this.context = app
        if (adapter == null) adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) return
        holder.acquire()
    }

    /** Closes the proxies; the next [acquire] or [refresh] binds them again. */
    fun release() {
        holder.release()
    }

    /**
     * Reads which devices the held proxies report as connected into [AudioConnections] in the
     * background, then runs the listeners on the main thread. Binds the proxies first when needed;
     * their arrival triggers a read of its own.
     */
    fun refresh(context: Context) {
        acquire(context)
        reader.request()
    }

    /** Runs [listener] on the main thread after every read. */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** Runs [action] once, after the first read that had both proxies (at once if one already did). */
    fun whenComplete(action: () -> Unit) {
        whenComplete += action
        reader.request()
    }

    private fun bind(profile: Int): Boolean {
        val app = context ?: return false
        return try {
            adapter?.getProfileProxy(app, serviceListener, profile) == true
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot bind the profile proxy $profile", e)
            false
        }
    }

    /** Background: the connected devices of each held proxy; true when both proxies were there. */
    private fun readConnected(): Boolean {
        for (profile in PROFILES) {
            val proxy = holder.proxy(profile) ?: continue
            try {
                AudioConnections.update(profile, proxy.connectedDevices.map { it.address })
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot list connected audio devices", e)
            }
        }
        return holder.complete()
    }

    private fun deliver(complete: Boolean) {
        listeners.forEach { it() }
        if (complete && whenComplete.isNotEmpty()) {
            val actions = whenComplete.toList()
            whenComplete.clear()
            actions.forEach { it() }
        }
    }
}
