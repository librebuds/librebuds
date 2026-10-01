// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.state.BudsRepository
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkError
import io.github.librebuds.state.LinkState
import io.github.librebuds.ui.model.NoiseControlMode
import io.github.librebuds.ui.model.Picker
import io.github.librebuds.ui.model.cancellationLevelPicker
import io.github.librebuds.ui.model.listedModes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Device screen state. [listedModes] are the noise-control modes the profile lists (before the
 * "show Off" preference, see [offeredModes]); [cancellationLevel] is the level picker, present
 * while cancellation is selected and the profile lists levels, with the optimistic level as its
 * current value.
 */
data class DeviceUi(
    val state: BudsState,
    val selectedNoiseMode: NoiseControlMode?,
    val pendingNoiseMode: NoiseControlMode?,
    val error: UiError?,
    val listedModes: List<NoiseControlMode> = NoiseControlMode.entries,
    val cancellationLevel: Picker? = null,
    val pendingLevel: Int? = null,
)

/** One pending noise-control request: a mode switch, or cancellation at an explicit [level]. */
private class PendingAnc(val mode: NoiseControlMode, val level: Int? = null)

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

/**
 * Screen state. A noise-mode or level tap shows immediately; the repository result confirms or
 * reverts it. Mode and level share one pending request, so a newer tap on either supersedes the
 * older one. [profileFor] looks a profile up by id.
 */
class DeviceViewModel(
    private val repository: BudsRepository,
    private val profileFor: (String) -> Profile = { ProfileRegistry.GENERIC },
) : ViewModel() {
    private val pending = MutableStateFlow<PendingAnc?>(null)
    private val failure = MutableStateFlow<Failure?>(null)
    private var job: Job? = null

    val ui: StateFlow<DeviceUi> = combine(repository.state, pending, failure) { state, request, fail -> build(state, request, fail) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, build(repository.state.value, null, null))

    private fun build(state: BudsState, request: PendingAnc?, fail: Failure?): DeviceUi {
        val profile = profileFor(state.profileId)
        val selected = request?.mode ?: NoiseControlMode.of(state.anc)
        val readLevel = state.anc?.takeIf { it.mode == AncMode.CANCELLATION }?.level
        val level = cancellationLevelPicker(profile, request?.level ?: readLevel)
            ?.takeIf { selected == NoiseControlMode.NOISE_CANCELLATION }
        return DeviceUi(
            state = state,
            selectedNoiseMode = selected,
            pendingNoiseMode = request?.mode,
            error = errorFor(state, fail),
            listedModes = listedModes(profile),
            cancellationLevel = level,
            pendingLevel = request?.level,
        )
    }

    /** A fresh request failure wins; otherwise the reason the link last ended, until the next connect. */
    private fun errorFor(state: BudsState, fail: Failure?): UiError? =
        fail?.takeIf { it.at == state.errorScope() }?.kind ?: state.lastError?.toUiError()

    fun selectNoiseMode(mode: NoiseControlMode) = send(PendingAnc(mode)) { repository.setAnc(mode.anc) }

    /** Turns cancellation on at [level], one of the level picker's options. */
    fun selectCancellationLevel(level: Int) =
        send(PendingAnc(NoiseControlMode.NOISE_CANCELLATION, level)) { repository.setAncLevel(level) }

    private fun send(request: PendingAnc, call: suspend () -> Result<AncState>) {
        job?.cancel()
        pending.value = request
        failure.value = null
        job = viewModelScope.launch {
            val result = call()
            // Identity: a repeated tap on the same mode or level is a newer request.
            if (pending.value !== request) return@launch
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
