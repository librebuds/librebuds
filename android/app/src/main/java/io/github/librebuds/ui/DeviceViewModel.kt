// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.librebuds.session.AncRejectedException
import io.github.librebuds.session.NotConnectedException
import io.github.librebuds.session.RequestTimeoutException
import io.github.librebuds.session.SessionClosedException
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
    val error: UiError?,
)

/** An error tied to the state it was reported against, so it clears once the state moves past it. */
private data class Failure(val kind: UiError, val at: BudsState)

private fun Throwable.toUiError(): UiError = when (this) {
    is AncRejectedException -> UiError.REJECTED
    is NotConnectedException -> UiError.NOT_CONNECTED
    is RequestTimeoutException, is SessionClosedException -> UiError.NO_REPLY
    else -> UiError.UNKNOWN
}

/** Screen state. A noise-mode tap shows immediately; the repository result confirms or reverts it. */
class DeviceViewModel(private val repository: BudsRepository) : ViewModel() {
    private val pending = MutableStateFlow<NoiseControlMode?>(null)
    private val failure = MutableStateFlow<Failure?>(null)
    private var job: Job? = null

    val ui: StateFlow<DeviceUi> = combine(repository.state, pending, failure) { state, pendingMode, fail ->
        DeviceUi(state, pendingMode ?: NoiseControlMode.of(state.anc), pendingMode, fail?.takeIf { it.at == state }?.kind)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DeviceUi(repository.state.value, NoiseControlMode.of(repository.state.value.anc), null, null))

    fun selectNoiseMode(mode: NoiseControlMode) {
        job?.cancel()
        pending.value = mode
        failure.value = null
        job = viewModelScope.launch {
            val result = repository.setAnc(mode.anc)
            if (pending.value != mode) return@launch
            pending.value = null
            result.onFailure { failure.value = Failure(it.toUiError(), repository.state.value) }
        }
    }

    /** Reclaims the link after another client took it (spec 8). */
    fun takeOver() {
        failure.value = null
        viewModelScope.launch {
            repository.takeOver().onFailure { failure.value = Failure(it.toUiError(), repository.state.value) }
        }
    }
}
