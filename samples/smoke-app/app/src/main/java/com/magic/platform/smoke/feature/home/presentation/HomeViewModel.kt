package com.magic.platform.smoke.feature.home.presentation

import com.magic.mvicore.android.PulseAndroidExecutionOwner
import com.magic.mvicore.android.PulseIntentExecutionDecision
import com.magic.mvicore.android.PulseSplitStoreViewModel
import com.magic.mvicore.android.PulseUiIntentExecutor
import com.magic.mvicore.android.androidPulseRuntimeConfig
import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.runtime.PulseRuntimeConfig
import com.magic.platform.smoke.feature.home.contract.HomeEffect
import com.magic.platform.smoke.feature.home.contract.HomeIntent
import com.magic.platform.smoke.feature.home.contract.HomeMutation
import com.magic.platform.smoke.feature.home.contract.HomeState

class HomeViewModel(
    runtimeConfig: PulseRuntimeConfig = androidPulseRuntimeConfig(),
    executionOwner: PulseAndroidExecutionOwner? = null,
) : PulseSplitStoreViewModel<
    HomeState,
    HomeIntent,
    HomeMutation,
    HomeEffect
>(
    initialState = HomeState(),
    mutationReducer = HomeReducer,
    uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
        when (intent) {
            HomeIntent.OnPrimaryClick -> context.mutate(HomeMutation.PrimaryClicked)
        }
        PulseIntentExecutionDecision.Completed
    },
    runtimeConfig = runtimeConfig,
    executionOwner = executionOwner,
) {
    fun onIntent(intent: HomeIntent): EnqueueResult = trySend(intent)
}
