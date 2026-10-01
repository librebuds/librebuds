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
import io.github.librebuds.overlay.ConnectionIslandSlot
import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.state.batterySummary
import io.github.librebuds.ui.model.Battery
import io.github.librebuds.ui.model.BatteryComponent
import io.github.librebuds.ui.model.BatteryStatus

/** How the case-open overlay looks: the LibrePods island at the top, or the card with artwork at the bottom. */
enum class PopupStyle { ISLAND, CARD }

/** How a case-open popup reaches the user. */
sealed interface PopupAction {
    /** The island at the top of the screen ([PopupStyle.ISLAND]). */
    data object Island : PopupAction
    /** The card with artwork at the bottom of the screen ([PopupStyle.CARD]). */
    data object Card : PopupAction
    data object Notification : PopupAction
    data object Nothing : PopupAction
}

/**
 * The overlay in the chosen [style] when allowed, else a notification; nothing when the popup is off
 * or neither is allowed. On a [locked] screen the overlay would not be seen (it stays behind the
 * keyguard), so only the notification is used.
 */
fun popupAction(
    enabled: Boolean,
    style: PopupStyle,
    canDrawOverlays: Boolean,
    notificationsAllowed: Boolean,
    locked: Boolean,
): PopupAction = when {
    !enabled -> PopupAction.Nothing
    canDrawOverlays && !locked -> if (style == PopupStyle.ISLAND) PopupAction.Island else PopupAction.Card
    notificationsAllowed -> PopupAction.Notification
    else -> PopupAction.Nothing
}

/** A case-open popup on screen, whichever [PopupStyle] draws it. */
interface CasePopup {
    /** False as soon as it starts closing. */
    val isOpen: Boolean

    /** The profile of the earbuds it shows, once opened. */
    val profileId: String?

    fun close()
}

/**
 * Whether [popup] keeps the connection island for [profileId] away: only one overlay is on screen at a
 * time, and an open popup for these earbuds already reports the connection. The style does not matter.
 */
fun popupBlocksIsland(popup: CasePopup?, profileId: String): Boolean =
    popup != null && popup.isOpen && popup.profileId == profileId

/**
 * Whether the connection island has to go once the popup is up: only an overlay popup (either
 * style) that was actually [shown] would overlap it. A notification, no popup, or an overlay that
 * could not be added (the notification fallback) leaves it alone.
 */
fun popupClosesIsland(islandOpen: Boolean, action: PopupAction, shown: Boolean): Boolean =
    islandOpen && shown && (action == PopupAction.Island || action == PopupAction.Card)

/** How [PopupPresenter.show] delivered a popup, for the log. */
fun deliveryDescription(action: PopupAction, shown: Boolean, notified: Boolean): String = when {
    action == PopupAction.Nothing -> "not delivered (popup off, or no overlay or notification permission)"
    shown -> "overlay ${if (action == PopupAction.Island) "island" else "card"}"
    notified && action == PopupAction.Notification -> "notification (locked screen or no overlay permission)"
    notified -> "notification (overlay could not be added)"
    else -> "not delivered (overlay failed, notifications not allowed)"
}

/** How long a case-open popup stays on screen without interaction, in either style. */
const val POPUP_AUTO_CLOSE_MILLIS = 12_000L

/** Shows the case-open popup, or its notification fallback. Main thread only. */
object PopupPresenter {
    private const val TAG = "PopupPresenter"
    private const val CHANNEL_ID = "popup"
    private const val NOTIFICATION_ID = 2
    private const val NOTIFICATION_TIMEOUT_MILLIS = 60_000L

    // Both popups keep only the application context, so holding one here leaks no activity.
    @SuppressLint("StaticFieldLeak")
    private var current: CasePopup? = null

    /** Shows the popup in the style chosen in Settings; [art] is only used by the card. */
    fun show(context: Context, model: PopupModel, art: PopupArt) {
        val preferences = AppPreferences(context)
        val action = popupAction(
            enabled = preferences.popupEnabled,
            style = preferences.popupStyle,
            canDrawOverlays = Settings.canDrawOverlays(context),
            notificationsAllowed = notificationsAllowed(context),
            locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true,
        )
        val shown = when (action) {
            PopupAction.Island -> showIsland(context, model)
            PopupAction.Card -> showCard(context, model, art)
            PopupAction.Notification, PopupAction.Nothing -> false
        }
        // Closed at once in the same main-thread turn the popup was added, so no frame shows both.
        if (popupClosesIsland(ConnectionIslandSlot.isOpen(), action, shown)) ConnectionIslandSlot.close()
        val overlayFailed = !shown && (action == PopupAction.Island || action == PopupAction.Card)
        val notified = action == PopupAction.Notification || overlayFailed && notificationsAllowed(context)
        if (notified) notify(context, model)
        Log.i(TAG, "popup for ${model.title}: ${deliveryDescription(action, shown, notified)}")
    }

    /** Whether a case-open popup for [profileId] is on screen (the connection island then stays away). */
    fun isShowing(profileId: String): Boolean = popupBlocksIsland(current, profileId)

    /** Closes the popup and its notification fallback, for example when the user turned the popup off. */
    fun dismiss(context: Context) {
        current?.close()
        current = null
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /** Replaces any popup still on screen (another pair of earbuds, say) with the card. */
    private fun showCard(context: Context, model: PopupModel, art: PopupArt): Boolean {
        current?.close()
        lateinit var created: PopupWindow
        created = PopupWindow(context) { if (current === created) current = null }
        current = created
        return created.open(model, art)
    }

    /** Replaces any popup still on screen with the island. */
    private fun showIsland(context: Context, model: PopupModel): Boolean {
        current?.close()
        lateinit var created: PopupIsland
        created = PopupIsland(context) { if (current === created) current = null }
        current = created
        return created.open(model)
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
