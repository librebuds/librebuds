// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds

import android.app.Application
import android.content.Context
import io.github.librebuds.state.BudsRepository
import io.github.librebuds.state.DemoBudsRepository
import io.github.librebuds.widget.WidgetUpdater
import kotlinx.coroutines.MainScope

class LibreBudsApp : Application() {
    /** Replaced by the Bluetooth-backed repository in M2b. */
    lateinit var repository: BudsRepository
        private set

    /** Lives as long as the process; hosts app-wide collectors such as the widget updater. */
    private val appScope = MainScope()

    override fun onCreate() {
        super.onCreate()
        repository = DemoBudsRepository()
        WidgetUpdater(this, repository, appScope).start()
    }

    companion object {
        fun from(context: Context): LibreBudsApp = context.applicationContext as LibreBudsApp
    }
}
