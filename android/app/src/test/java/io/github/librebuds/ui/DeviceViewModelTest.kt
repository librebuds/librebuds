// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.BatteryState
import io.github.librebuds.session.AncRejectedException
import io.github.librebuds.session.RequestTimeoutException
import io.github.librebuds.state.BudsRepository
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkError
import io.github.librebuds.state.LinkState
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.ui.model.NoiseControlMode
import io.github.librebuds.ui.model.keyOf
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeRepository : BudsRepository {
        val flow = MutableStateFlow(BudsState(link = LinkState.CONNECTED, anc = AncState(modeCode = 0, level = 3)))
        val calls = mutableListOf<CompletableDeferred<Result<AncState>>>()
        override val state: StateFlow<BudsState> = flow
        override suspend fun setAnc(mode: AncMode): Result<AncState> {
            val deferred = CompletableDeferred<Result<AncState>>()
            calls.add(deferred)
            val result = withContext(NonCancellable) { deferred.await() }
            result.onSuccess { flow.value = flow.value.copy(anc = it) }
            return result
        }
        override suspend fun setAncLevel(level: Int): Result<AncState> {
            levels += level
            val deferred = CompletableDeferred<Result<AncState>>()
            calls.add(deferred)
            val result = withContext(NonCancellable) { deferred.await() }
            result.onSuccess { flow.value = flow.value.copy(anc = it) }
            return result
        }
        val levels = mutableListOf<Int>()
        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
    }

    private val freebuds5 = ProfileRegistry.fromJson(
        listOf("""{"id":"freebuds-5","name":"FreeBuds 5","capabilities":{"anc":{"modes":["off","cancellation"],"cancellationLevels":[3,1,0]}}}"""),
    ).profiles.single()

    private fun fb5Repository(anc: AncState) = FakeRepository().apply { flow.value = flow.value.copy(profileId = "freebuds-5", anc = anc) }

    private fun fb5ViewModel(repo: FakeRepository) =
        DeviceViewModel(repo) { id -> if (id == "freebuds-5") freebuds5 else ProfileRegistry.GENERIC }

    @Test
    fun listsOnlyTheProfileModes() = runTest(dispatcher) {
        val vm = fb5ViewModel(fb5Repository(AncState(modeCode = 0, level = 3)))
        advanceUntilIdle()
        assertEquals(listOf(NoiseControlMode.OFF, NoiseControlMode.NOISE_CANCELLATION), vm.ui.value.listedModes)
        assertEquals(NoiseControlMode.entries, DeviceViewModel(FakeRepository()).ui.value.listedModes)
    }

    @Test
    fun levelRowOnlyWhileCancellationIsSelected() = runTest(dispatcher) {
        val repo = fb5Repository(AncState(modeCode = 0, level = 3))
        val vm = fb5ViewModel(repo)
        advanceUntilIdle()
        assertNull(vm.ui.value.cancellationLevel)

        repo.flow.value = repo.flow.value.copy(anc = AncState(modeCode = 1, level = 1))
        advanceUntilIdle()
        val picker = vm.ui.value.cancellationLevel!!
        assertEquals(1, picker.current)
        assertEquals(listOf(3, 1, 0), picker.options.map { it.code })
        assertEquals(listOf("dynamic", "cozy", "general"), picker.options.map { it.key })

        // A model without levels never shows the row.
        val plain = DeviceViewModel(FakeRepository().apply { flow.value = flow.value.copy(anc = AncState(1, 3)) })
        advanceUntilIdle()
        assertNull(plain.ui.value.cancellationLevel)
    }

    @Test
    fun levelRowWhileSwitchingToCancellationHasNoLevelYet() = runTest(dispatcher) {
        val repo = fb5Repository(AncState(modeCode = 0, level = 3))
        val vm = fb5ViewModel(repo)
        vm.selectNoiseMode(NoiseControlMode.NOISE_CANCELLATION)
        advanceUntilIdle()
        // Off reports a remembered level that is not the one cancellation will use.
        assertNull(vm.ui.value.cancellationLevel!!.current)
        repo.calls[0].complete(Result.success(AncState(modeCode = 1, level = 3)))
        advanceUntilIdle()
        assertEquals(3, vm.ui.value.cancellationLevel!!.current)
    }

    @Test
    fun levelShowsAtOnceThenConfirmed() = runTest(dispatcher) {
        val repo = fb5Repository(AncState(modeCode = 1, level = 3))
        val vm = fb5ViewModel(repo)
        vm.selectCancellationLevel(0)
        advanceUntilIdle()
        assertEquals(listOf(0), repo.levels)
        assertEquals(0, vm.ui.value.cancellationLevel!!.current)
        assertEquals(0, vm.ui.value.pendingLevel)
        assertEquals(NoiseControlMode.NOISE_CANCELLATION, vm.ui.value.pendingNoiseMode)

        repo.calls[0].complete(Result.success(AncState(modeCode = 1, level = 0)))
        advanceUntilIdle()
        assertEquals(0, vm.ui.value.cancellationLevel!!.current)
        assertNull(vm.ui.value.pendingLevel)
        assertNull(vm.ui.value.error)
    }

    @Test
    fun rejectedLevelRevertsToTheReadBackAndShowsRejected() = runTest(dispatcher) {
        val repo = fb5Repository(AncState(modeCode = 1, level = 3))
        val vm = fb5ViewModel(repo)
        vm.selectCancellationLevel(1)
        advanceUntilIdle()
        assertEquals(1, vm.ui.value.cancellationLevel!!.current)
        repo.calls[0].complete(Result.failure(AncRejectedException()))
        advanceUntilIdle()
        assertEquals(3, vm.ui.value.cancellationLevel!!.current)
        assertNull(vm.ui.value.pendingLevel)
        assertEquals(UiError.REJECTED, vm.ui.value.error)
    }

    @Test
    fun modeTapSupersedesAPendingLevel() = runTest(dispatcher) {
        val repo = fb5Repository(AncState(modeCode = 1, level = 3))
        val vm = fb5ViewModel(repo)
        vm.selectCancellationLevel(1)
        advanceUntilIdle()
        vm.selectNoiseMode(NoiseControlMode.OFF)
        advanceUntilIdle()
        assertNull(vm.ui.value.cancellationLevel)
        repo.calls[0].complete(Result.failure(AncRejectedException()))
        advanceUntilIdle()
        assertEquals(NoiseControlMode.OFF, vm.ui.value.pendingNoiseMode)
        assertNull(vm.ui.value.error)
        repo.calls[1].complete(Result.success(AncState(modeCode = 0, level = 3)))
        advanceUntilIdle()
        assertEquals(NoiseControlMode.OFF, vm.ui.value.selectedNoiseMode)
        assertNull(vm.ui.value.pendingNoiseMode)
    }

    @Test
    fun unknownReadBackLevelKeepsItsRawCode() = runTest(dispatcher) {
        val vm = fb5ViewModel(fb5Repository(AncState(modeCode = 1, level = 5)))
        advanceUntilIdle()
        val picker = vm.ui.value.cancellationLevel!!
        assertEquals(5, picker.current)
        assertNull(picker.keyOf(5))
        assertEquals(listOf(3, 1, 0), picker.options.map { it.code })
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showsRequestedModeWhilePendingThenConfirmed() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = DeviceViewModel(repo)
        vm.selectNoiseMode(NoiseControlMode.AWARENESS)
        advanceUntilIdle()
        assertEquals(NoiseControlMode.AWARENESS, vm.ui.value.selectedNoiseMode)
        assertEquals(NoiseControlMode.AWARENESS, vm.ui.value.pendingNoiseMode)

        repo.calls[0].complete(Result.success(AncState(modeCode = 2, level = 2)))
        advanceUntilIdle()
        assertEquals(NoiseControlMode.AWARENESS, vm.ui.value.selectedNoiseMode)
        assertNull(vm.ui.value.pendingNoiseMode)
    }

    @Test
    fun revertsSelectionOnFailure() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = DeviceViewModel(repo)
        vm.selectNoiseMode(NoiseControlMode.NOISE_CANCELLATION)
        advanceUntilIdle()
        repo.calls[0].complete(Result.failure(AncRejectedException()))
        advanceUntilIdle()
        assertEquals(NoiseControlMode.OFF, vm.ui.value.selectedNoiseMode)
        assertNull(vm.ui.value.pendingNoiseMode)
        assertEquals(UiError.REJECTED, vm.ui.value.error)
    }

    @Test
    fun mapsRequestTimeoutToNoReply() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = DeviceViewModel(repo)
        vm.selectNoiseMode(NoiseControlMode.NOISE_CANCELLATION)
        advanceUntilIdle()
        repo.calls[0].complete(Result.failure(RequestTimeoutException(CommandId(0x2B, 0x2A))))
        advanceUntilIdle()
        assertEquals(UiError.NO_REPLY, vm.ui.value.error)
    }

    // Final review M3: a battery report or a newer timestamp does not make the error stale.
    @Test
    fun errorSurvivesBatteryAndTimestampUpdates() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = DeviceViewModel(repo)
        vm.selectNoiseMode(NoiseControlMode.NOISE_CANCELLATION)
        advanceUntilIdle()
        repo.calls[0].complete(Result.failure(AncRejectedException()))
        advanceUntilIdle()
        assertEquals(UiError.REJECTED, vm.ui.value.error)

        repo.flow.value = repo.flow.value.copy(updatedAtMillis = 1, battery = BatteryState(80, 80, 70, 50, false, false, false))
        advanceUntilIdle()
        assertEquals(UiError.REJECTED, vm.ui.value.error)
    }

    @Test
    fun errorClearsWhenNoiseModeOrLinkChanges() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = DeviceViewModel(repo)
        vm.selectNoiseMode(NoiseControlMode.NOISE_CANCELLATION)
        advanceUntilIdle()
        repo.calls[0].complete(Result.failure(AncRejectedException()))
        advanceUntilIdle()
        assertEquals(UiError.REJECTED, vm.ui.value.error)

        repo.flow.value = repo.flow.value.copy(anc = AncState(modeCode = 2, level = 2))
        advanceUntilIdle()
        assertNull(vm.ui.value.error)

        vm.selectNoiseMode(NoiseControlMode.NOISE_CANCELLATION)
        advanceUntilIdle()
        repo.calls[1].complete(Result.failure(AncRejectedException()))
        advanceUntilIdle()
        assertEquals(UiError.REJECTED, vm.ui.value.error)
        repo.flow.value = repo.flow.value.copy(link = LinkState.DISCONNECTED)
        advanceUntilIdle()
        assertNull(vm.ui.value.error)
    }

    // Final review I2: a session that gave up leaves a sticky NO_REPLY in the state.
    @Test
    fun showsNoReplyFromStateAfterGiveUp() = runTest(dispatcher) {
        val repo = FakeRepository()
        repo.flow.value = repo.flow.value.copy(link = LinkState.DISCONNECTED, lastError = LinkError.NO_REPLY)
        val vm = DeviceViewModel(repo)
        advanceUntilIdle()
        assertEquals(UiError.NO_REPLY, vm.ui.value.error)
    }

    @Test
    fun latestRequestWins() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = DeviceViewModel(repo)
        vm.selectNoiseMode(NoiseControlMode.NOISE_CANCELLATION)
        advanceUntilIdle()
        vm.selectNoiseMode(NoiseControlMode.AWARENESS)
        advanceUntilIdle()
        repo.calls[0].complete(Result.failure(IllegalStateException("superseded")))
        advanceUntilIdle()
        assertEquals(NoiseControlMode.AWARENESS, vm.ui.value.pendingNoiseMode)
        assertNull(vm.ui.value.error)
        repo.calls[1].complete(Result.success(AncState(modeCode = 2, level = 2)))
        advanceUntilIdle()
        assertNull(vm.ui.value.pendingNoiseMode)
        assertEquals(NoiseControlMode.AWARENESS, vm.ui.value.selectedNoiseMode)
    }
}
