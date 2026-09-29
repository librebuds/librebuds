// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import io.github.librebuds.beacon.BeaconScanner
import io.github.librebuds.bt.LinkFactory
import io.github.librebuds.bt.RfcommLinkFactory
import io.github.librebuds.bt.isAudioConnected
import io.github.librebuds.bt.refreshAudioConnections
import io.github.librebuds.diag.FrameLog
import io.github.librebuds.profile.ProfileAssets
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.session.BudsController
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.state.BudsRepository
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.DemoBudsRepository
import io.github.librebuds.state.LinkState
import io.github.librebuds.state.PersistedState
import io.github.librebuds.state.StateStore
import io.github.librebuds.widget.WidgetUpdater
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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

    /** Device profiles bundled as assets; loaded once for the controller and the earbud picker. */
    lateinit var registry: ProfileRegistry
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
        registry = ProfileAssets.load(this)
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val stateStore = StateStore(this)
        controller = BudsController(
            linkFactory = adapter?.let(::RfcommLinkFactory) ?: LinkFactory { throw IOException("No Bluetooth adapter") },
            registry = registry,
            scope = appScope,
            isAudioConnected = ::isAudioConnected,
            frameLog = frameLog,
            // Whether audio to the earbuds is up is not known yet: the profile proxies answer later.
            // A stored take-over is kept for now and settled in keepAudioConnectionsFresh().
            initial = stateStore.load()?.toBudsState(audioUp = { true }) ?: BudsState(),
        )
        persistState(stateStore)
        val preferences = AppPreferences(this)
        repository = if (BuildConfig.DEBUG && preferences.demoMode) DemoBudsRepository() else controller
        WidgetUpdater(this, repository, appScope).start()
        keepAudioConnectionsFresh()
        // A no-op when already registered in this boot; false without BLUETOOTH_SCAN or with Bluetooth off.
        BeaconScanner.ensureStarted(this)
        BeaconScanner.watchBluetooth(this)
    }

    /**
     * Saves the controller's state (never the demo data) once it has been stable for a second,
     * so the next process starts from the last known values and a take-over outlives a restart.
     * Only changes to the stored part count: settings or host updates do not restart the wait.
     */
    @OptIn(FlowPreview::class)
    private fun persistState(store: StateStore) {
        appScope.launch {
            controller.state.drop(1)
                .map(PersistedState::from)
                .distinctUntilChanged()
                .debounce(SAVE_DEBOUNCE_MILLIS)
                .collect(store::save)
        }
    }

    /**
     * Refreshes [io.github.librebuds.bt.AudioConnections] at start and whenever the earbuds connect,
     * so a later link drop can tell a takeover from a disconnect even before any ACL broadcast arrived.
     * Once both audio profiles answered at start, a restored take-over without audio to those
     * earbuds (for example after a reboot) is cleared, so they connect again on their own.
     */
    private fun keepAudioConnectionsFresh() {
        var answers = 0
        refreshAudioConnections(this) {
            if (++answers == AUDIO_PROFILE_COUNT) controller.clearTakeOver(keepWhile = ::isAudioConnected)
        }
        appScope.launch {
            controller.state.map { it.link }.distinctUntilChanged().collect { link ->
                if (link == LinkState.CONNECTED) refreshAudioConnections(this@LibreBudsApp)
            }
        }
    }

    companion object {
        private const val SAVE_DEBOUNCE_MILLIS = 1000L

        /** A2DP and headset: the profiles [refreshAudioConnections] asks. */
        private const val AUDIO_PROFILE_COUNT = 2

        fun from(context: Context): LibreBudsApp = context.applicationContext as LibreBudsApp
    }
}
