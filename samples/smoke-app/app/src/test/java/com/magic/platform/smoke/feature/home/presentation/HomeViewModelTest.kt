package com.magic.platform.smoke.feature.home.presentation

import com.magic.mvicore.android.PulseIntentExecutionResult
import com.magic.mvicore.android.testing.PulseSplitTestConfig
import com.magic.mvicore.android.testing.runPulseSplitTest
import com.magic.mvicore.contract.EnqueueResult
import com.magic.platform.smoke.feature.home.contract.HomeEffect
import com.magic.platform.smoke.feature.home.contract.HomeIntent
import com.magic.platform.smoke.feature.home.contract.HomeMutation
import com.magic.platform.smoke.feature.home.contract.HomeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun publicIntentRunsTheRealExecutorAndReducer() = runPulseSplitTest {
        val host = splitHost<HomeState, HomeIntent, HomeMutation, HomeEffect, HomeViewModel> {
                config, owner -> HomeViewModel(config, owner)
        }

        assertEquals(
            PulseIntentExecutionResult.Completed,
            host.sendAndDrain(HomeIntent.OnPrimaryClick),
        )
        assertEquals(1, host.viewModel.state.value.interactionCount)
        assertEquals(2, host.transitionProbe.snapshot().size)
        host.failureProbe.assertEmpty()
    }

    @Test
    fun callbackReportsFullAndClosedWithoutQueuingExtraCoroutines() = runPulseSplitTest {
        val host = splitHost<HomeState, HomeIntent, HomeMutation, HomeEffect, HomeViewModel>(
            config = PulseSplitTestConfig(mailboxCapacity = 1),
        ) { config, owner -> HomeViewModel(config, owner) }

        assertTrue(host.viewModel.onIntent(HomeIntent.OnPrimaryClick) is EnqueueResult.Enqueued)
        assertEquals(EnqueueResult.Full, host.viewModel.onIntent(HomeIntent.OnPrimaryClick))
        runCurrent()
        assertEquals(1, host.viewModel.state.value.interactionCount)
        assertTrue(host.viewModel.onIntent(HomeIntent.OnPrimaryClick) is EnqueueResult.Enqueued)
        runCurrent()
        assertEquals(2, host.viewModel.state.value.interactionCount)

        host.closeAndDrain()
        assertTrue(host.viewModel.onIntent(HomeIntent.OnPrimaryClick) is EnqueueResult.Rejected)
        assertEquals(2, host.viewModel.state.value.interactionCount)
    }
}
