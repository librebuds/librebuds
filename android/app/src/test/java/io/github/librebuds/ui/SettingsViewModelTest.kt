// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.command.GestureSetting
import io.github.librebuds.protocol.command.HostAction
import io.github.librebuds.protocol.command.HostRow
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.session.AncRejectedException
import io.github.librebuds.session.RequestTimeoutException
import io.github.librebuds.session.SettingUnavailableException
import io.github.librebuds.protocol.CommandId
import io.github.librebuds.state.BudsRepository
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.DeviceSettings
import io.github.librebuds.state.LinkState
import io.github.librebuds.state.SettingChange
import io.github.librebuds.ui.model.applyTo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val profile: Profile = ProfileRegistry.fromJson(
        listOf(
            """
            {"id": "test", "name": "Test", "capabilities": {
              "wear": {"verified": null}, "lowLatency": {"verified": "2026-09-30"}, "multipoint": {"verified": null},
              "gestures": {"verified": null, "doubleTap": {"options": {"-1": "off", "1": "pause"}}}
            }}
            """.trimIndent(),
        ),
    ).profiles.single()

    private class FakeRepository : BudsRepository {
        val flow = MutableStateFlow(
            BudsState(
                link = LinkState.CONNECTED,
                profileId = "test",
                settings = DeviceSettings(
                    wearDetection = false,
                    lowLatency = false,
                    gestures = mapOf(Gesture.DOUBLE_TAP to GestureSetting(left = 1, right = 1, inCall = null, supported = emptyList())),
                ),
                multipointEnabled = false,
                hosts = listOf(host("AA:AA:AA:AA:AA:01", preferred = true), host("AA:AA:AA:AA:AA:02", preferred = false)),
            ),
        )
        val calls = mutableListOf<Pair<SettingChange, CompletableDeferred<Result<Unit>>>>()
        var refreshes = 0
        override val state: StateFlow<BudsState> = flow
        override suspend fun setAnc(mode: AncMode): Result<AncState> = Result.failure(UnsupportedOperationException())
        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
        override suspend fun apply(change: SettingChange): Result<Unit> {
            val deferred = CompletableDeferred<Result<Unit>>()
            calls.add(change to deferred)
            val result = withContext(NonCancellable) { deferred.await() }
            // Like the controller: the read-back that confirmed the change lands in the state.
            result.onSuccess { flow.value = change.applyTo(flow.value) }
            return result
        }
        override suspend fun refreshHosts(): Result<List<HostRow>> {
            refreshes++
            return Result.failure(RequestTimeoutException(CommandId(0x2B, 0x31)))
        }

        companion object {
            fun host(mac: String, preferred: Boolean) =
                HostRow(index = 0, count = 2, mac = mac, name = "Host", connection = 0, preferred = preferred, autoConnect = null)
        }
    }

    private fun viewModel(repo: FakeRepository) = SettingsViewModel(repo) { profile }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showsRequestedValueWhilePendingThenConfirmed() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)
        vm.apply(SettingChange.Wear(true))
        advanceUntilIdle()
        assertEquals(true, vm.ui.value.model.wear)
        assertEquals(setOf("wear"), vm.ui.value.pending)
        assertEquals(false, repo.flow.value.settings.wearDetection)

        repo.calls[0].second.complete(Result.success(Unit))
        advanceUntilIdle()
        assertEquals(true, vm.ui.value.model.wear)
        assertTrue(vm.ui.value.pending.isEmpty())
        assertNull(vm.ui.value.error)
    }

    @Test
    fun revertsAndReportsRejectedOnFailure() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)
        vm.apply(SettingChange.GestureChange(Gesture.DOUBLE_TAP, left = -1, right = null, inCall = null))
        advanceUntilIdle()
        assertEquals(-1, vm.ui.value.model.gestures.single().left?.current)

        repo.calls[0].second.complete(Result.failure(AncRejectedException()))
        advanceUntilIdle()
        assertEquals(1, vm.ui.value.model.gestures.single().left?.current)
        assertTrue(vm.ui.value.pending.isEmpty())
        assertEquals(UiError.REJECTED, vm.ui.value.error)
    }

    @Test
    fun controlsArePendingIndependently() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)
        vm.apply(SettingChange.Wear(true))
        vm.apply(SettingChange.LowLatencyChange(true))
        advanceUntilIdle()
        assertEquals(2, repo.calls.size)
        assertEquals(setOf("wear", "lowLatency"), vm.ui.value.pending)
        assertEquals(true, vm.ui.value.model.wear)
        assertEquals(true, vm.ui.value.model.lowLatency)

        // The low-latency change fails; the wear change stays pending and shown.
        repo.calls[1].second.complete(Result.failure(AncRejectedException()))
        advanceUntilIdle()
        assertEquals(setOf("wear"), vm.ui.value.pending)
        assertEquals(true, vm.ui.value.model.wear)
        assertEquals(false, vm.ui.value.model.lowLatency)
        assertEquals(UiError.REJECTED, vm.ui.value.error)

        repo.calls[0].second.complete(Result.success(Unit))
        advanceUntilIdle()
        assertTrue(vm.ui.value.pending.isEmpty())
        assertEquals(true, vm.ui.value.model.wear)
    }

    @Test
    fun gestureSidesArePendingIndependently() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)
        vm.apply(SettingChange.GestureChange(Gesture.DOUBLE_TAP, left = -1, right = null, inCall = null))
        vm.apply(SettingChange.GestureChange(Gesture.DOUBLE_TAP, left = null, right = -1, inCall = null))
        advanceUntilIdle()
        assertEquals(2, repo.calls.size)
        val control = vm.ui.value.model.gestures.single()
        assertEquals(-1, control.left?.current)
        assertEquals(-1, control.right?.current)
    }

    @Test
    fun newerChangeForTheSameControlWins() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)
        vm.apply(SettingChange.Wear(true))
        advanceUntilIdle()
        vm.apply(SettingChange.Wear(false))
        advanceUntilIdle()
        repo.calls[0].second.complete(Result.failure(AncRejectedException()))
        advanceUntilIdle()
        assertEquals(setOf("wear"), vm.ui.value.pending)
        assertEquals(false, vm.ui.value.model.wear)
        assertNull(vm.ui.value.error)

        repo.calls[1].second.complete(Result.success(Unit))
        advanceUntilIdle()
        assertTrue(vm.ui.value.pending.isEmpty())
    }

    @Test
    fun unansweredSettingReportsNoReply() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)
        vm.apply(SettingChange.LowLatencyChange(true))
        advanceUntilIdle()
        repo.calls[0].second.complete(Result.failure(SettingUnavailableException()))
        advanceUntilIdle()
        assertEquals(UiError.NO_REPLY, vm.ui.value.error)
        assertEquals(false, vm.ui.value.model.lowLatency)
    }

    @Test
    fun preferredHostShowsAtOnceAcrossHosts() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)
        vm.apply(SettingChange.PreferredHost("aa:aa:aa:aa:aa:02"))
        advanceUntilIdle()
        assertEquals(listOf(false, true), vm.ui.value.state.hosts.map { it.preferred })
        vm.apply(SettingChange.HostCommand(HostAction.CONNECT, "AA:AA:AA:AA:AA:01"))
        advanceUntilIdle()
        assertEquals(setOf("preferredHost", "host.AA:AA:AA:AA:AA:01"), vm.ui.value.pending)
        assertTrue(vm.ui.value.state.hosts[0].connected)
    }

    @Test
    fun errorClearsWhenTheSettingsMoveOn() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)
        vm.apply(SettingChange.Wear(true))
        advanceUntilIdle()
        repo.calls[0].second.complete(Result.failure(AncRejectedException()))
        advanceUntilIdle()
        assertEquals(UiError.REJECTED, vm.ui.value.error)

        repo.flow.value = repo.flow.value.copy(updatedAtMillis = 5)
        advanceUntilIdle()
        assertEquals(UiError.REJECTED, vm.ui.value.error)

        repo.flow.value = repo.flow.value.copy(settings = repo.flow.value.settings.copy(wearDetection = true))
        advanceUntilIdle()
        assertNull(vm.ui.value.error)
    }

    @Test
    fun refreshHostsMapsFailureAndEndsRefreshing() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)
        vm.refreshHosts()
        advanceUntilIdle()
        assertEquals(1, repo.refreshes)
        assertFalse(vm.ui.value.refreshingHosts)
        assertEquals(UiError.NO_REPLY, vm.ui.value.error)
    }
}
