// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.librebuds.state.BudsRepository
import io.github.librebuds.state.BudsState
import io.github.librebuds.ui.model.NoiseControlMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeviceUi(
    val state: BudsState,
    val selectedNoiseMode: NoiseControlMode?,
    val pendingNoiseMode: NoiseControlMode?,
    val error: String?,
)

/** Screen state. A noise-mode tap shows immediately; the repository result confirms or reverts it. */
class DeviceViewModel(private val repository: BudsRepository) : ViewModel() {
    private val pending = MutableStateFlow<NoiseControlMode?>(null)
    private val error = MutableStateFlow<String?>(null)
    private var job: Job? = null

    val ui: StateFlow<DeviceUi> = combine(repository.state, pending, error) { state, pendingMode, err ->
        DeviceUi(state, pendingMode ?: NoiseControlMode.of(state.anc), pendingMode, err)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DeviceUi(repository.state.value, NoiseControlMode.of(repository.state.value.anc), null, null))

    fun selectNoiseMode(mode: NoiseControlMode) {
        job?.cancel()
        pending.value = mode
        error.value = null
        job = viewModelScope.launch {
            val result = repository.setAnc(mode.anc)
            if (pending.value != mode) return@launch
            pending.value = null
            result.onFailure { error.value = it.message ?: "The earbuds rejected the setting" }
        }
    }
}
