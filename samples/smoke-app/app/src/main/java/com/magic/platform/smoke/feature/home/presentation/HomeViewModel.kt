package com.magic.platform.smoke.feature.home.presentation

import androidx.lifecycle.viewModelScope
import com.magic.mvicore.android.PulseIntentExecutionDecision
import com.magic.mvicore.android.PulseSplitStoreViewModel
import com.magic.mvicore.android.PulseUiIntentExecutor
import com.magic.platform.smoke.feature.home.contract.HomeEffect
import com.magic.platform.smoke.feature.home.contract.HomeIntent
import com.magic.platform.smoke.feature.home.contract.HomeMutation
import com.magic.platform.smoke.feature.home.contract.HomeState
import kotlinx.coroutines.launch

class HomeViewModel : PulseSplitStoreViewModel<
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
) {
    fun onIntent(intent: HomeIntent) {
        viewModelScope.launch {
            send(intent)
        }
    }
}
