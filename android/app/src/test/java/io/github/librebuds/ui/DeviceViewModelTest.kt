// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.state.BudsRepository
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.LinkState
import io.github.librebuds.ui.model.NoiseControlMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeRepository : BudsRepository {
        val flow = MutableStateFlow(BudsState(link = LinkState.CONNECTED, anc = AncState(modeCode = 0, level = 3)))
        var next = CompletableDeferred<Result<AncState>>()
        override val state: StateFlow<BudsState> = flow
        override suspend fun setAnc(mode: AncMode): Result<AncState> {
            val result = next.await()
            result.onSuccess { flow.value = flow.value.copy(anc = it) }
            return result
        }
        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
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

        repo.next.complete(Result.success(AncState(modeCode = 2, level = 2)))
        advanceUntilIdle()
        assertEquals(NoiseControlMode.AWARENESS, vm.ui.value.selectedNoiseMode)
        assertNull(vm.ui.value.pendingNoiseMode)
    }

    @Test
    fun revertsSelectionOnFailure() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = DeviceViewModel(repo)
        vm.selectNoiseMode(NoiseControlMode.NOISE_CANCELLATION)
        repo.next.complete(Result.failure(IllegalStateException("rejected")))
        advanceUntilIdle()
        assertEquals(NoiseControlMode.OFF, vm.ui.value.selectedNoiseMode)
        assertNull(vm.ui.value.pendingNoiseMode)
        assertNotNull(vm.ui.value.error)
    }

    @Test
    fun latestRequestWins() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = DeviceViewModel(repo)
        vm.selectNoiseMode(NoiseControlMode.NOISE_CANCELLATION)
        advanceUntilIdle()
        vm.selectNoiseMode(NoiseControlMode.AWARENESS)
        advanceUntilIdle()
        assertEquals(NoiseControlMode.AWARENESS, vm.ui.value.pendingNoiseMode)
    }
}
