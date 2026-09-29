// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.librebuds.companion.AssociationStore
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.ui.AppRoot
import io.github.librebuds.ui.DeviceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(
            this,
            viewModelFactory { initializer { DeviceViewModel(LibreBudsApp.from(this@MainActivity).repository) } }
        )[DeviceViewModel::class.java]
        val preferences = AppPreferences(this)
        val associationStore = AssociationStore(this)
        setContent { AppRoot(viewModel, preferences, associationStore) }
    }
}
