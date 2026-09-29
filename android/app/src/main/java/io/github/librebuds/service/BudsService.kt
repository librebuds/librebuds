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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.MainActivity
import io.github.librebuds.R
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkState
import io.github.librebuds.state.batterySummary
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_connection), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                watchState()
                app.appScope.launch { app.controller.connect(address, name) }
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
        scope.cancel()
        super.onDestroy()
    }

    private fun enterForeground(state: BudsState): Boolean = try {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(state), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        true
    } catch (e: ForegroundServiceStartNotAllowedException) {
        Log.w(TAG, "Not allowed to start the connection service now", e)
        stopSelf()
        false
    } catch (e: SecurityException) {
        // The connectedDevice type needs a granted Bluetooth permission at this moment.
        Log.w(TAG, "Missing permission for the connection service", e)
        stopSelf()
        false
    }

    /** Mirrors the controller state into the notification and stops once the earbuds are gone. */
    private fun watchState() {
        if (collector?.isActive == true) return
        val controller = LibreBudsApp.from(this).controller
        val manager = getSystemService(NotificationManager::class.java)
        collector = scope.launch {
            var previous: LinkState? = null
            var hadConnected = false
            controller.state.collect { state ->
                if (state.link == LinkState.CONNECTED) hadConnected = true
                if (serviceShouldStop(previous, state.link, hadConnected)) {
                    stopService()
                    return@collect
                }
                previous = state.link
                // Without POST_NOTIFICATIONS the update is dropped; the service keeps running.
                manager.notify(NOTIFICATION_ID, notification(state))
            }
        }
    }

    private fun stopService() {
        collector?.cancel()
        collector = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
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
