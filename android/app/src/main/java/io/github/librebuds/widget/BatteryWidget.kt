// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.R
import io.github.librebuds.state.BudsState

/** Home-screen widget with the left, right and case battery levels. */
class BatteryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, render(context, LibreBudsApp.from(context).repository.state.value))
    }

    companion object {
        fun render(context: Context, state: BudsState): RemoteViews {
            val battery = state.battery.takeIf { state.isConnected }
            return RemoteViews(context.packageName, R.layout.widget_battery).apply {
                setTextViewText(R.id.widget_battery_left, level(battery?.left, battery?.leftCharging))
                setTextViewText(R.id.widget_battery_right, level(battery?.right, battery?.rightCharging))
                setTextViewText(R.id.widget_battery_case, level(battery?.case, battery?.caseCharging))
            }
        }

        private fun level(value: Int?, charging: Boolean?): String {
            if (value == null) return "--"
            return if (charging == true) "⚡$value%" else "$value%"
        }
    }
}
