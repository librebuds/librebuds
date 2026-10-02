// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.librebuds.protocol.command.HostRow
import io.github.librebuds.protocol.command.Side
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.state.BudsRepository
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.DeviceSettings
import io.github.librebuds.state.LinkState
import io.github.librebuds.state.SettingChange
import io.github.librebuds.ui.model.SettingsModel
import io.github.librebuds.ui.model.applyTo
import io.github.librebuds.ui.model.controlKey
import io.github.librebuds.ui.model.settingsModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Settings screen state. [state] already shows every pending change (optimistic values);
 * [pending] holds the control keys still waiting for the device.
 */
data class SettingsUi(
    val state: BudsState,
    val model: SettingsModel,
    val pending: Set<String>,
    val refreshingHosts: Boolean,
    val error: UiError?,
)

/** The parts of the state a settings error is about; it clears once one of them moves on. */
private data class SettingsScope(
    val link: LinkState,
    val address: String?,
    val settings: DeviceSettings,
    val multipointEnabled: Boolean?,
    val hosts: List<HostRow>,
)

private fun BudsState.settingsScope() = SettingsScope(link, address, settings, multipointEnabled, hosts)

private data class SettingsFailure(val kind: UiError, val at: SettingsScope)

private class Inputs(val pending: Map<String, SettingChange>, val failure: SettingsFailure?, val refreshing: Boolean)

/**
 * Per-model settings and multipoint hosts. Each change shows at once and is confirmed or reverted
 * by the repository's result, like [DeviceViewModel.selectNoiseMode]; every control keeps its own
 * pending change, so changing one does not cancel another. [profileFor] looks a profile up by id.
 */
class SettingsViewModel(
    private val repository: BudsRepository,
    private val profileFor: (String) -> Profile,
) : ViewModel() {
    private val pending = MutableStateFlow<Map<String, SettingChange>>(emptyMap())
    private val failure = MutableStateFlow<SettingsFailure?>(null)
    private val refreshing = MutableStateFlow(false)
    private val jobs = mutableMapOf<String, Job>()

    private val inputs = combine(pending, failure, refreshing) { p, f, r -> Inputs(p, f, r) }

    val ui: StateFlow<SettingsUi> = combine(repository.state, inputs) { state, input -> build(state, input) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, build(repository.state.value, Inputs(emptyMap(), null, false)))

    private fun build(state: BudsState, input: Inputs): SettingsUi {
        val shown = input.pending.values.fold(state) { acc, change -> change.applyTo(acc) }
        val profile = profileFor(state.profileId)
        return SettingsUi(
            state = shown,
            model = settingsModel(profile, shown),
            pending = input.pending.keys,
            refreshingHosts = input.refreshing,
            error = input.failure?.takeIf { it.at == state.settingsScope() }?.kind,
        )
    }

    /** Sends [change]; a newer change for the same control supersedes it. */
    fun apply(change: SettingChange) {
        val key = change.controlKey()
        jobs.remove(key)?.cancel()
        pending.update { it + (key to change) }
        failure.value = null
        jobs[key] = viewModelScope.launch {
            val result = repository.apply(change)
            // Identity, not equality: a repeated tap on the same value is a newer request.
            if (pending.value[key] !== change) return@launch
            pending.update { it - key }
            jobs.remove(key)
            result.onFailure { failure.value = SettingsFailure(it.toUiError(), repository.state.value.settingsScope()) }
        }
    }

    /** Rings ([ring] true) or silences one earbud; a failure shows like a settings error. */
    fun ring(side: Side, ring: Boolean) {
        failure.value = null
        viewModelScope.launch {
            repository.ring(side, ring).onFailure {
                failure.value = SettingsFailure(it.toUiError(), repository.state.value.settingsScope())
            }
        }
    }

    /** Re-reads the multipoint host list (pull to refresh). */
    fun refreshHosts() {
        if (refreshing.value) return
        refreshing.value = true
        failure.value = null
        viewModelScope.launch {
            try {
                repository.refreshHosts().onFailure {
                    failure.value = SettingsFailure(it.toUiError(), repository.state.value.settingsScope())
                }
            } finally {
                refreshing.value = false
            }
        }
    }
}
