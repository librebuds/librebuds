// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import io.github.librebuds.bt.LinkFactory
import io.github.librebuds.bt.RfcommLinkFactory
import io.github.librebuds.bt.isAudioConnected
import io.github.librebuds.bt.refreshAudioConnections
import io.github.librebuds.diag.FrameLog
import io.github.librebuds.profile.ProfileAssets
import io.github.librebuds.session.BudsController
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.state.BudsRepository
import io.github.librebuds.state.DemoBudsRepository
import io.github.librebuds.state.LinkState
import io.github.librebuds.widget.WidgetUpdater
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

class LibreBudsApp : Application() {
    /** What screens, tile and widgets show: the controller, or demo data in debug builds when enabled. */
    lateinit var repository: BudsRepository
        private set

    /** The Bluetooth session; the connection service drives it. */
    lateinit var controller: BudsController
        private set

    /** Recent raw frames for the diagnostics export. */
    lateinit var frameLog: FrameLog
        private set

    /**
     * Lives as long as the process and is confined to the main thread; hosts the controller, the
     * widget updater and connect attempts, which must outlive the service that started them.
     */
    val appScope = MainScope()

    override fun onCreate() {
        super.onCreate()
        frameLog = FrameLog()
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        controller = BudsController(
            linkFactory = adapter?.let(::RfcommLinkFactory) ?: LinkFactory { throw IOException("No Bluetooth adapter") },
            registry = ProfileAssets.load(this),
            scope = appScope,
            isAudioConnected = ::isAudioConnected,
            frameLog = frameLog,
        )
        repository = if (BuildConfig.DEBUG && AppPreferences(this).demoMode) DemoBudsRepository() else controller
        WidgetUpdater(this, repository, appScope).start()
        keepAudioConnectionsFresh()
    }

    /**
     * Refreshes [io.github.librebuds.bt.AudioConnections] at start and whenever the earbuds connect,
     * so a later link drop can tell a takeover from a disconnect even before any ACL broadcast arrived.
     */
    private fun keepAudioConnectionsFresh() {
        refreshAudioConnections(this)
        appScope.launch {
            controller.state.map { it.link }.distinctUntilChanged().collect { link ->
                if (link == LinkState.CONNECTED) refreshAudioConnections(this@LibreBudsApp)
            }
        }
    }

    companion object {
        fun from(context: Context): LibreBudsApp = context.applicationContext as LibreBudsApp
    }
}
