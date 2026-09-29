// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds

import android.app.Application
import android.content.Context
import io.github.librebuds.state.BudsRepository
import io.github.librebuds.state.DemoBudsRepository

class LibreBudsApp : Application() {
    /** Replaced by the Bluetooth-backed repository in M2b. */
    lateinit var repository: BudsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = DemoBudsRepository()
    }

    companion object {
        fun from(context: Context): LibreBudsApp = context.applicationContext as LibreBudsApp
    }
}
