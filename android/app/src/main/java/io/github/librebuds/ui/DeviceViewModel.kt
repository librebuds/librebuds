// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.session.AncRejectedException
import io.github.librebuds.session.NotConnectedException
import io.github.librebuds.session.RequestTimeoutException
import io.github.librebuds.session.SessionClosedException
import io.github.librebuds.state.BudsRepository
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkError
import io.github.librebuds.state.LinkState
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

/**
 * The parts of the state an error is about. A battery report or a refreshed timestamp does not
 * make an error stale; a different link, noise mode or device does.
 */
private data class ErrorScope(val link: LinkState, val anc: AncState?, val address: String?)

private fun BudsState.errorScope() = ErrorScope(link, anc, address)

/** An error tied to the state it was reported against, so it clears once that state moves past it. */
private data class Failure(val kind: UiError, val at: ErrorScope)

private fun LinkError.toUiError(): UiError = when (this) {
    LinkError.NO_REPLY -> UiError.NO_REPLY
}

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
        DeviceUi(state, pendingMode ?: NoiseControlMode.of(state.anc), pendingMode, errorFor(state, fail))
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        repository.state.value.let { DeviceUi(it, NoiseControlMode.of(it.anc), null, errorFor(it, null)) },
    )

    /** A fresh request failure wins; otherwise the reason the link last ended, until the next connect. */
    private fun errorFor(state: BudsState, fail: Failure?): UiError? =
        fail?.takeIf { it.at == state.errorScope() }?.kind ?: state.lastError?.toUiError()

    fun selectNoiseMode(mode: NoiseControlMode) {
        job?.cancel()
        pending.value = mode
        failure.value = null
        job = viewModelScope.launch {
            val result = repository.setAnc(mode.anc)
            if (pending.value != mode) return@launch
            pending.value = null
            result.onFailure { failure.value = Failure(it.toUiError(), repository.state.value.errorScope()) }
        }
    }

    /** Reclaims the link after another client took it (spec 8). */
    fun takeOver() {
        failure.value = null
        viewModelScope.launch {
            repository.takeOver().onFailure { failure.value = Failure(it.toUiError(), repository.state.value.errorScope()) }
        }
    }
}
