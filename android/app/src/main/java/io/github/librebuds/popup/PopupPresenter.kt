// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.librebuds.MainActivity
import io.github.librebuds.R
import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.state.batterySummary
import io.github.librebuds.ui.model.Battery
import io.github.librebuds.ui.model.BatteryComponent
import io.github.librebuds.ui.model.BatteryStatus

/** How a case-open popup reaches the user. */
sealed interface PopupAction {
    data object Overlay : PopupAction
    data object Notification : PopupAction
    data object Nothing : PopupAction
}

/**
 * The overlay when allowed, else a notification; nothing when the popup is off or neither is allowed.
 * On a [locked] screen the overlay would not be seen (it stays behind the keyguard), so only the
 * notification is used.
 */
fun popupAction(enabled: Boolean, canDrawOverlays: Boolean, notificationsAllowed: Boolean, locked: Boolean): PopupAction = when {
    !enabled -> PopupAction.Nothing
    canDrawOverlays && !locked -> PopupAction.Overlay
    notificationsAllowed -> PopupAction.Notification
    else -> PopupAction.Nothing
}

/** Shows the case-open popup, or its notification fallback. Main thread only. */
object PopupPresenter {
    private const val TAG = "PopupPresenter"
    private const val CHANNEL_ID = "popup"
    private const val NOTIFICATION_ID = 2
    private const val NOTIFICATION_TIMEOUT_MILLIS = 60_000L

    // PopupWindow keeps only the application context, so holding it here leaks no activity.
    @SuppressLint("StaticFieldLeak")
    private var window: PopupWindow? = null

    fun show(context: Context, model: PopupModel, art: PopupArt) {
        val action = popupAction(
            enabled = AppPreferences(context).popupEnabled,
            canDrawOverlays = Settings.canDrawOverlays(context),
            notificationsAllowed = notificationsAllowed(context),
            locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true,
        )
        when (action) {
            PopupAction.Overlay -> if (!showOverlay(context, model, art) && notificationsAllowed(context)) notify(context, model)
            PopupAction.Notification -> notify(context, model)
            PopupAction.Nothing -> Unit
        }
    }

    /** Whether a case-open popup for [profileId] is on screen (the connection island then stays away). */
    fun isShowing(profileId: String): Boolean = window?.let { it.isOpen && it.profileId == profileId } == true

    /** Closes the popup and its notification fallback, for example when the user turned the popup off. */
    fun dismiss(context: Context) {
        window?.close()
        window = null
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /** Replaces any popup still on screen (another pair of earbuds, say) with a new one. */
    private fun showOverlay(context: Context, model: PopupModel, art: PopupArt): Boolean {
        window?.close()
        lateinit var created: PopupWindow
        created = PopupWindow(context) { if (window === created) window = null }
        window = created
        return created.open(model, art)
    }

    private fun notificationsAllowed(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun notify(context: Context, model: PopupModel) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_popup), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_buds)
            .setContentTitle(model.title)
            .setContentText(batterySummary(model.batteries.toBatteryState()))
            .setContentIntent(open)
            .setAutoCancel(true)
            // A repeat for the same earbuds updates the notification quietly instead of alerting again.
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(NOTIFICATION_TIMEOUT_MILLIS)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission revoked", e)
        }
    }

    private fun List<Battery>.toBatteryState(): BatteryState {
        fun of(component: Int) = find { it.component == component && it.status != BatteryStatus.DISCONNECTED }
        val left = of(BatteryComponent.LEFT)
        val right = of(BatteryComponent.RIGHT)
        val case = of(BatteryComponent.CASE)
        return BatteryState(
            overall = null,
            left = left?.level,
            right = right?.level,
            case = case?.level,
            leftCharging = left?.let { it.status == BatteryStatus.CHARGING },
            rightCharging = right?.let { it.status == BatteryStatus.CHARGING },
            caseCharging = case?.let { it.status == BatteryStatus.CHARGING },
        )
    }
}
