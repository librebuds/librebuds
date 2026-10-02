// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.MainActivity
import io.github.librebuds.R
import io.github.librebuds.bt.AudioProfiles
import io.github.librebuds.bt.BtWorker
import io.github.librebuds.bt.CoalescedReader
import io.github.librebuds.bt.isAudioConnected
import io.github.librebuds.bt.probeAclConnections
import io.github.librebuds.companion.DetectedBuds
import io.github.librebuds.companion.Presence
import io.github.librebuds.companion.isBluetoothOn
import io.github.librebuds.companion.readDetectedBuds
import io.github.librebuds.companion.trackPresence
import io.github.librebuds.diag.EventLog
import io.github.librebuds.overlay.ConnectionIslandSlot
import io.github.librebuds.overlay.IslandHost
import io.github.librebuds.overlay.IslandWindow
import io.github.librebuds.overlay.islandBatteryLevel
import io.github.librebuds.overlay.islandShouldShow
import io.github.librebuds.popup.PopupPresenter
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkState
import io.github.librebuds.state.PairHistory
import io.github.librebuds.state.batterySummary
import io.github.librebuds.ui.components.ProductArt
import io.github.librebuds.ui.model.Battery
import io.github.librebuds.ui.model.toUiBatteries
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The background side of the app, as a `connectedDevice` foreground service. It is started at boot, when
 * the app opens and when paired FreeBuds connect, and keeps running while Bluetooth is on and at least
 * one pair of FreeBuds is paired ([serviceShouldRun]). Meanwhile it follows the ACL, A2DP and headset
 * state of every paired FreeBuds and connects the control channel of whichever pair is connected to the
 * phone ([AutoConnectPlanner]); no companion device association is needed for that.
 *
 * The connect itself runs on the app scope so stopping the service never cancels a blocking socket
 * connect halfway; [io.github.librebuds.session.BudsController.disconnect] is what aborts it.
 */
class BudsService : Service() {
    private val scope = MainScope()
    private var collector: Job? = null
    private val planner = AutoConnectPlanner()

    // The latest connect launched by this service and the address it is for.
    private var connectJob: Job? = null
    private var connectAddress: String? = null

    // Stopping with the latest startId never discards a START that is still being delivered.
    private var lastStartId = 0
    private var island: IslandWindow? = null
    private var receiverRegistered = false

    // What evaluate() was asked for since the last decision; main thread only.
    private val pendingReasons = mutableListOf<String>()
    private var pendingPreferred: String? = null

    /**
     * The paired FreeBuds, Bluetooth on/off and the pair history are binder calls and preference reads:
     * read on [BtWorker] once per burst of broadcasts, then decided on the main thread.
     */
    private val snapshots = CoalescedReader(
        background = { BtWorker.execute { it.run() } },
        main = { BtWorker.main { it.run() } },
        read = { Snapshot(isBluetoothOn(this), readDetectedBuds(this), PairHistory(this).all()) },
        deliver = ::decide,
    )

    private val onProfilesRead: () -> Unit = { evaluate("profiles") }

    // False once this instance is destroyed; a read still in flight then decides nothing.
    private var alive = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_connection), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.notification_channel_connection_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val filter = IntentFilter().apply { WATCHED_ACTIONS.forEach(::addAction) }
        // Only the system sends these protected broadcasts; exported just makes sure they are delivered.
        registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
        AudioProfiles.addListener(onProfilesRead)
        AudioProfiles.acquire(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Enter the foreground before anything else: a service started with
        // startForegroundService() must do so even if it is about to stop.
        if (!enterForeground(LibreBudsApp.from(this).controller.state.value)) return START_NOT_STICKY
        watchState()
        // A null intent is a restart by the system after it killed the process (START_STICKY).
        val preferred = intent?.getStringExtra(EXTRA_ADDRESS)
        event(this, "start (${if (intent == null) "restarted" else "requested"}${preferred?.let { ", for ${EventLog.maskMac(it)}" } ?: ""})")
        sweep(preferred)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        alive = false
        if (receiverRegistered) unregisterReceiver(bluetoothReceiver)
        receiverRegistered = false
        AudioProfiles.removeListener(onProfilesRead)
        // The proxies were bound once for this process; nothing needs them once the service is gone.
        AudioProfiles.release()
        // Nothing keeps a session alive without the service; drop it rather than leak it.
        val link = LibreBudsApp.from(this).controller.state.value.link
        if (link == LinkState.CONNECTED || link == LinkState.CONNECTING) LibreBudsApp.from(this).controller.disconnect()
        ConnectionIslandSlot.unregister()
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
        event(this, "foreground refused: ${e.javaClass.simpleName}")
        stopSelfResult(lastStartId)
        false
    } catch (e: SecurityException) {
        // The connectedDevice type needs a granted Bluetooth permission at this moment.
        Log.w(TAG, "Missing permission for the connection service", e)
        event(this, "foreground refused: missing permission")
        stopSelfResult(lastStartId)
        false
    }

    /**
     * Re-reads which FreeBuds are connected: the ACL probe and the profile proxies, which answer later
     * and evaluate again. Then decides at once with what is known, preferring [preferred] when the
     * user opened that pair in the app.
     */
    private fun sweep(preferred: String? = null) {
        // Queued on the serial worker before the evaluation's own read, so that read sees the probe.
        BtWorker.execute { readDetectedBuds(this)?.let { buds -> probeAclConnections(this, buds.map { it.address }) } }
        AudioProfiles.refresh(this)
        evaluate("sweep", preferred)
    }

    /**
     * Stops when the service is no longer needed, otherwise connects the pair the planner picks. The
     * Bluetooth reads run in the background; a burst of calls ends in one decision ([decide]).
     */
    private fun evaluate(reason: String, preferred: String? = null) {
        if (!running) return
        pendingReasons += reason
        if (preferred != null) pendingPreferred = preferred
        snapshots.request()
    }

    private fun decide(snapshot: Snapshot) {
        if (!running || !alive) return
        val reason = pendingReasons.distinct().joinToString(", ").ifEmpty { "update" }
        val preferred = pendingPreferred
        pendingReasons.clear()
        pendingPreferred = null
        val buds = snapshot.buds
        if (!serviceShouldRun(snapshot.bluetoothOn, connectGranted = buds != null, bondedFreeBuds = buds?.size ?: 0)) {
            event(this, "stopping ($reason): bluetooth ${if (snapshot.bluetoothOn) "on" else "off"}, ${buds?.size ?: "no permission for"} paired FreeBuds")
            stopService()
            return
        }
        val state = LibreBudsApp.from(this).controller.state.value
        // The state collector may not have run yet (first start): a restored take-over must count already.
        planner.onState(state)
        val connected = buds.orEmpty().filter { isAudioConnected(it.address) }.map { it.address } +
            // The controller is talking to its pair right now, whatever the trackers missed.
            listOfNotNull(state.address?.takeIf { state.link == LinkState.CONNECTED || state.link == LinkState.CONNECTING })
        val target = preferred?.takeIf { address ->
            connected.any { it.equals(address, ignoreCase = true) } && !planner.isHeldElsewhere(address)
        } ?: planner.target(connected, snapshot.history, state)
        if (target == null || !shouldLaunchConnect(state, target)) return
        if (connectJob?.isActive == true && target.equals(connectAddress, ignoreCase = true)) return
        val name = buds.orEmpty().firstOrNull { it.address.equals(target, ignoreCase = true) }?.bondedName
        event(this, "connect ${EventLog.maskMac(target)} ($reason, link ${state.link})")
        launchConnect(target, name)
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val address = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.address
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> address?.let {
                    trackPresence(Presence.APPEARED, it)
                    BtWorker.execute { if (isFreeBuds(it)) PairHistory(context).record(it) }
                    planner.onLinkUp(it)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> address?.let {
                    trackPresence(Presence.DISAPPEARED, it)
                    planner.onGone(it)
                    // Earbuds another device held are gone from the phone: that take-over is over, so they may
                    // connect on their own next time. Only on this event, never on a guess at process start,
                    // where a restored take-over waits for the profile proxies (see LibreBudsApp).
                    val controller = LibreBudsApp.from(context).controller
                    if (it.equals(controller.state.value.address, ignoreCase = true)) controller.clearTakeOver(keepWhile = ::isAudioConnected)
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED, BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val profileState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    // Audio coming up is often when the control channel accepts a connect that failed on the bare ACL.
                    if (profileState == BluetoothProfile.STATE_CONNECTED) address?.let(planner::onLinkUp)
                    // The held proxies are read once the burst of profile broadcasts is over; onProfilesRead evaluates.
                    AudioProfiles.refresh(context)
                }
            }
            evaluate(intent.action?.substringAfterLast('.') ?: "broadcast")
        }
    }

    private fun isFreeBuds(address: String): Boolean =
        readDetectedBuds(this).orEmpty().any { it.address.equals(address, ignoreCase = true) }

    /** Mirrors the controller state into the notification and the island, and re-plans once a link ends. */
    private fun watchState() {
        if (collector?.isActive == true) return
        val controller = LibreBudsApp.from(this).controller
        val manager = getSystemService(NotificationManager::class.java)
        collector = scope.launch {
            var previous: LinkState? = null
            controller.state.collect { state ->
                planner.onState(state)
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
                // Without POST_NOTIFICATIONS the update is dropped; the service keeps running.
                manager.notify(NOTIFICATION_ID, notification(state))
                // A link that just ended may leave another connected pair to switch to.
                if (state.link == LinkState.DISCONNECTED && previous != null && previous != LinkState.DISCONNECTED) evaluate("link ended")
                previous = state.link
            }
        }
    }

    private fun launchConnect(address: String, name: String?) {
        val app = LibreBudsApp.from(this)
        planner.onLaunched(address)
        connectAddress = address
        connectJob = app.appScope.launch { app.controller.connect(address, name) }
    }

    /** Shows the connection island; runs on the main thread, where the state collector runs. */
    private fun showIsland(state: BudsState) {
        if (islandHost.islandOpen) return
        val window = IslandWindow(this)
        island = window
        // Removed at once (no close animation) when a case-open popup arrives, so they never overlap.
        ConnectionIslandSlot.register(isOpen = { islandHost.islandOpen }, close = { window.forceClose() })
        val shape = LibreBudsApp.from(this).registry.profiles.firstOrNull { it.id == state.profileId }?.art ?: "generic"
        window.show(state.name ?: getString(R.string.app_name), islandBatteryLevel(state.battery), islandHost, ProductArt.thumbnail(shape, state.profileId))
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
        val content = notificationContent(
            state,
            appName = getString(R.string.app_name),
            connected = getString(R.string.buds_connected),
            connecting = getString(R.string.connecting),
            takenOver = getString(R.string.controlled_by_other),
            waiting = getString(R.string.notification_waiting),
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // The service must stay in the foreground, but its notification need not show: the system lets
        // the user turn this channel off, and this action goes straight there.
        val hide = PendingIntent.getActivity(
            this, 1,
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_buds)
            .setColor(ContextCompat.getColor(this, R.color.notification_accent))
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notification_hide), hide)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    companion object {
        private const val TAG = "BudsService"
        private const val CHANNEL_ID = "connection"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "io.github.librebuds.action.START"
        private const val EXTRA_ADDRESS = "address"

        private val WATCHED_ACTIONS = listOf(
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED,
            BluetoothDevice.ACTION_BOND_STATE_CHANGED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothAdapter.ACTION_STATE_CHANGED,
        )

        @Volatile
        private var running = false

        private fun event(context: Context, msg: String) = LibreBudsApp.from(context).eventLog.record(TAG, msg)

        /**
         * Makes sure the service runs, when [serviceShouldRun] says it should; logs instead of crashing
         * when the system refuses. [preferred] is a pair the user opened in the app: when it is connected
         * to the phone, the service switches to it (never reclaiming one another device holds).
         */
        fun start(context: Context, preferred: String? = null) {
            val buds = readDetectedBuds(context)
            val bluetoothOn = isBluetoothOn(context)
            if (!serviceShouldRun(bluetoothOn, connectGranted = buds != null, bondedFreeBuds = buds?.size ?: 0)) {
                if (!running) event(context, "not starting: bluetooth ${if (bluetoothOn) "on" else "off"}, ${buds?.size ?: "no permission for"} paired FreeBuds")
                return
            }
            val intent = Intent(context, BudsService::class.java).setAction(ACTION_START).putExtra(EXTRA_ADDRESS, preferred)
            try {
                context.startForegroundService(intent)
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Not allowed to start the connection service", e)
                event(context, "service start refused: ${e.javaClass.simpleName}")
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Cannot start the connection service", e)
                event(context, "service start failed: ${e.javaClass.simpleName}")
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot start the connection service", e)
                event(context, "service start failed: ${e.javaClass.simpleName}")
            }
        }

        /**
         * Earbuds left the phone. The running service follows that through its own receiver; when it
         * is not running, a take-over is cleared (the earbuds are gone), so they may connect again.
         */
        fun onEarbudsGone(context: Context) {
            if (running) return
            LibreBudsApp.from(context).controller.clearTakeOver(keepWhile = ::isAudioConnected)
        }
    }
}

/** What [BudsService] reads in the background before deciding: Bluetooth on, the paired FreeBuds (null without permission), the pair history. */
private data class Snapshot(val bluetoothOn: Boolean, val buds: List<DetectedBuds>?, val history: Map<String, Long>)

/** The notification's title and line. */
data class NotificationContent(val title: String, val text: String)

/**
 * What the persistent notification says: the pair and its model with the battery while connected,
 * the pair and what is going on while connecting or held by another device, and a quiet waiting line
 * (titled with the app) while no FreeBuds are connected, rather than a "Not connected" alarm.
 */
fun notificationContent(state: BudsState, appName: String, connected: String, connecting: String, takenOver: String, waiting: String): NotificationContent {
    val name = state.name ?: appName
    return when (state.link) {
        LinkState.CONNECTED -> NotificationContent(
            name,
            listOfNotNull(state.device.model?.takeIf { it != state.name }, state.battery?.let(::batterySummary)).joinToString(" · ").ifEmpty { connected },
        )
        LinkState.CONNECTING -> NotificationContent(name, connecting)
        LinkState.TAKEN_OVER -> NotificationContent(name, takenOver)
        LinkState.DISCONNECTED -> NotificationContent(appName, waiting)
    }
}
