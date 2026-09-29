// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import io.github.librebuds.bt.AclTracker
import io.github.librebuds.bt.LinkFactory
import io.github.librebuds.bt.RfcommLinkFactory
import io.github.librebuds.diag.FrameLog
import io.github.librebuds.profile.ProfileAssets
import io.github.librebuds.session.BudsController
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.state.BudsRepository
import io.github.librebuds.state.DemoBudsRepository
import io.github.librebuds.widget.WidgetUpdater
import kotlinx.coroutines.MainScope
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
            isAudioConnected = AclTracker::isConnected,
            frameLog = frameLog,
        )
        repository = if (BuildConfig.DEBUG && AppPreferences(this).demoMode) DemoBudsRepository() else controller
        WidgetUpdater(this, repository, appScope).start()
    }

    companion object {
        fun from(context: Context): LibreBudsApp = context.applicationContext as LibreBudsApp
    }
}
