// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log
import io.github.librebuds.qs.BudsTileService
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.state.BudsRepository
import io.github.librebuds.state.BudsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Pushes every earbud state change to the home-screen widgets and the Quick Settings tile. */
class WidgetUpdater(
    context: Context,
    private val repository: BudsRepository,
    private val scope: CoroutineScope,
) {
    private val context = context.applicationContext

    fun start() {
        scope.launch {
            repository.state.collect { state ->
                // One failed push must not stop later updates.
                runCatching { push(state) }.onFailure { Log.w(TAG, "Widget/tile update failed", it) }
            }
        }
    }

    private fun push(state: BudsState) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, BatteryWidget::class.java)).takeIf { it.isNotEmpty() }
            ?.let { manager.updateAppWidget(it, BatteryWidget.render(context, state)) }
        manager.getAppWidgetIds(ComponentName(context, NoiseControlWidget::class.java)).takeIf { it.isNotEmpty() }
            ?.let { manager.updateAppWidget(it, NoiseControlWidget.render(context, state, AppPreferences(context).showOffMode)) }
        TileService.requestListeningState(context, ComponentName(context, BudsTileService::class.java))
    }

    private companion object {
        const val TAG = "WidgetUpdater"
    }
}
