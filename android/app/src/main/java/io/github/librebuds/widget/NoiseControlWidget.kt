// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.R
import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.state.BudsState
import io.github.librebuds.ui.model.NoiseControlMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Home-screen widget with one button per noise-control mode; the active mode is highlighted. */
class NoiseControlWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, render(context, LibreBudsApp.from(context).repository.state.value))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_NOISE_MODE) {
            super.onReceive(context, intent)
            return
        }
        val mode = AncMode.of(intent.getIntExtra(EXTRA_MODE, -1)) ?: return
        val repository = LibreBudsApp.from(context).repository
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Broadcast receivers get about ten seconds; the result arrives through the state flow.
                val result = withTimeoutOrNull(SET_TIMEOUT_MILLIS) { repository.setAnc(mode) }
                result?.onFailure { Log.w(TAG, "Setting noise mode $mode failed", it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SET_NOISE_MODE = "io.github.librebuds.action.SET_NOISE_MODE"
        const val EXTRA_MODE = "mode"
        private const val TAG = "NoiseControlWidget"
        private const val SET_TIMEOUT_MILLIS = 8_000L

        private class Button(val mode: NoiseControlMode, val container: Int, val icon: Int, val label: Int)

        private val buttons = listOf(
            Button(NoiseControlMode.OFF, R.id.widget_mode_off, R.id.widget_mode_off_icon, R.id.widget_mode_off_label),
            Button(NoiseControlMode.NOISE_CANCELLATION, R.id.widget_mode_cancellation, R.id.widget_mode_cancellation_icon, R.id.widget_mode_cancellation_label),
            Button(NoiseControlMode.AWARENESS, R.id.widget_mode_awareness, R.id.widget_mode_awareness_icon, R.id.widget_mode_awareness_label),
        )

        fun render(context: Context, state: BudsState): RemoteViews {
            val selected = NoiseControlMode.of(state.anc).takeIf { state.isConnected }
            val selectedColor = context.getColor(R.color.widget_on_accent)
            val normalColor = context.getColor(R.color.widget_text)
            return RemoteViews(context.packageName, R.layout.widget_noise_control).apply {
                buttons.forEach { button ->
                    val active = button.mode == selected
                    val color = if (active) selectedColor else normalColor
                    setInt(button.container, "setBackgroundResource", if (active) R.drawable.widget_mode_selected else 0)
                    setInt(button.icon, "setColorFilter", color)
                    setTextColor(button.label, color)
                    setOnClickPendingIntent(button.container, modeIntent(context, button.mode.anc))
                }
            }
        }

        private fun modeIntent(context: Context, mode: AncMode): PendingIntent {
            val intent = Intent(context, NoiseControlWidget::class.java)
                .setAction(ACTION_SET_NOISE_MODE)
                .putExtra(EXTRA_MODE, mode.code)
            // Extras do not make PendingIntents distinct, so each mode needs its own request code.
            return PendingIntent.getBroadcast(context, mode.code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
