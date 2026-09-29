// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.MainActivity
import io.github.librebuds.R
import io.github.librebuds.overlay.IslandHost
import io.github.librebuds.overlay.IslandWindow
import io.github.librebuds.overlay.islandBatteryLevel
import io.github.librebuds.overlay.islandShouldShow
import io.github.librebuds.popup.PopupPresenter
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkState
import io.github.librebuds.state.batterySummary
import io.github.librebuds.ui.model.Battery
import io.github.librebuds.ui.model.toUiBatteries
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the earbud connection alive while they are around, as a `connectedDevice` foreground service.
 * The connect itself runs on the app scope so stopping the service never cancels a blocking socket
 * connect halfway; [BudsController.disconnect] is what aborts it.
 */
class BudsService : Service() {
    private val scope = MainScope()
    private var collector: Job? = null
    private var stopTracker = ServiceStopTracker()

    // The latest connect launched by this service; it runs on the app scope (see the class KDoc).
    private var connectJob: Job? = null

    // Stopping with the latest startId never discards a START that is still being delivered.
    private var lastStartId = 0
    private var island: IslandWindow? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_connection), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val app = LibreBudsApp.from(this)
        when (intent?.action) {
            ACTION_START -> {
                // Enter the foreground before anything else: a service started with
                // startForegroundService() must do so even if it is about to stop.
                if (!enterForeground(app.controller.state.value)) return START_NOT_STICKY
                val address = intent.getStringExtra(EXTRA_ADDRESS)
                if (address == null) {
                    stopService()
                    return START_NOT_STICKY
                }
                val name = intent.getStringExtra(EXTRA_NAME)
                // Launch before the collector starts, so its first state already sees the active connect.
                if (shouldLaunchConnect(app.controller.state.value, address)) launchConnect(address, name)
                watchState()
            }
            else -> {
                app.controller.disconnect()
                stopService()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        // Nothing keeps a session alive without the service; drop it rather than leak it.
        val link = LibreBudsApp.from(this).controller.state.value.link
        if (link == LinkState.CONNECTED || link == LinkState.CONNECTING) LibreBudsApp.from(this).controller.disconnect()
        island?.forceClose()
        island = null
        scope.cancel()
        super.onDestroy()
    }

    private fun enterForeground(state: BudsState): Boolean = try {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(state), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        true
    } catch (e: ForegroundServiceStartNotAllowedException) {
        Log.w(TAG, "Not allowed to start the connection service now", e)
        stopSelfResult(lastStartId)
        false
    } catch (e: SecurityException) {
        // The connectedDevice type needs a granted Bluetooth permission at this moment.
        Log.w(TAG, "Missing permission for the connection service", e)
        stopSelfResult(lastStartId)
        false
    }

    /** Mirrors the controller state into the notification and stops once the earbuds are gone. */
    private fun watchState() {
        if (collector?.isActive == true) return
        val controller = LibreBudsApp.from(this).controller
        val manager = getSystemService(NotificationManager::class.java)
        stopTracker = ServiceStopTracker()
        collector = scope.launch {
            var previous: LinkState? = null
            controller.state.collect { state ->
                if (stopTracker.onState(state.link, connectActive = connectJob?.isActive == true)) {
                    stopService()
                    return@collect
                }
                val islandWanted = islandShouldShow(
                    previous = previous,
                    current = state.link,
                    enabled = AppPreferences(this@BudsService).showIsland,
                    canDrawOverlays = Settings.canDrawOverlays(this@BudsService),
                    popupShowing = PopupPresenter.isShowing(state.profileId),
                )
                if (islandWanted) {
                    showIsland(state)
                }
                island?.update(state.battery.toUiBatteries())
                previous = state.link
                // Without POST_NOTIFICATIONS the update is dropped; the service keeps running.
                manager.notify(NOTIFICATION_ID, notification(state))
            }
        }
    }

    private fun launchConnect(address: String, name: String?) {
        val app = LibreBudsApp.from(this)
        val job = app.appScope.launch { app.controller.connect(address, name) }
        connectJob = job
        // A stop deferred while this connect ran applies once it finished (the state may not change again).
        scope.launch {
            job.join()
            if (connectJob === job && stopTracker.onConnectFinished(app.controller.state.value.link)) stopService()
        }
    }

    /** Shows the connection island; runs on the main thread, where the state collector runs. */
    private fun showIsland(state: BudsState) {
        if (islandHost.islandOpen) return
        val window = IslandWindow(this)
        island = window
        window.show(state.name ?: getString(R.string.app_name), islandBatteryLevel(state.battery), islandHost)
    }

    private val islandHost = object : IslandHost {
        override var islandOpen = false

        override fun batteries(): List<Battery> = LibreBudsApp.from(this@BudsService).controller.state.value.battery.toUiBatteries()

        override fun takeOver() {
            val app = LibreBudsApp.from(this@BudsService)
            app.appScope.launch { app.controller.takeOver() }
        }

        override fun openApp() {
            startActivity(Intent(this@BudsService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    /** Stops unless a newer START is still pending; that START then keeps the service running. */
    private fun stopService() {
        if (!stopSelfResult(lastStartId)) return
        collector?.cancel()
        collector = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun notification(state: BudsState): Notification {
        val text = when (state.link) {
            LinkState.CONNECTED -> batterySummary(state.battery)
            LinkState.CONNECTING -> getString(R.string.connecting)
            LinkState.TAKEN_OVER -> getString(R.string.controlled_by_other)
            LinkState.DISCONNECTED -> getString(R.string.not_connected)
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_buds)
            .setContentTitle(state.name ?: getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    companion object {
        private const val TAG = "BudsService"
        private const val CHANNEL_ID = "connection"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "io.github.librebuds.action.START"
        private const val ACTION_STOP = "io.github.librebuds.action.STOP"
        private const val EXTRA_ADDRESS = "address"
        private const val EXTRA_NAME = "name"

        @Volatile
        private var running = false

        /** Starts or refreshes the connection to [address]; logs instead of crashing when the system refuses. */
        fun start(context: Context, address: String, name: String?) {
            val intent = Intent(context, BudsService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ADDRESS, address)
                .putExtra(EXTRA_NAME, name)
            try {
                context.startForegroundService(intent)
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Not allowed to start the connection service", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Cannot start the connection service", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot start the connection service", e)
            }
        }

        /** Disconnects and stops the service; a no-op when it is not running. */
        fun stop(context: Context) {
            if (!running) return
            try {
                context.startService(Intent(context, BudsService::class.java).setAction(ACTION_STOP))
            } catch (e: IllegalStateException) {
                // Background start refused: disconnecting directly makes the running service stop itself.
                Log.w(TAG, "Cannot deliver stop to the connection service", e)
                LibreBudsApp.from(context).controller.disconnect()
            }
        }
    }
}
