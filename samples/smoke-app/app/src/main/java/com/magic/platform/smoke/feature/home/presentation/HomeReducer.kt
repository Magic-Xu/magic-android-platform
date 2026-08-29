package com.magic.platform.smoke.feature.home.presentation

import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.platform.smoke.feature.home.contract.HomeEffect
import com.magic.platform.smoke.feature.home.contract.HomeMutation
import com.magic.platform.smoke.feature.home.contract.HomeState

object HomeReducer : PulseMutationReducer<HomeState, HomeMutation, HomeEffect> {
    override fun reduce(
        previous: HomeState,
        mutation: HomeMutation,
    ): ReduceOutcome<HomeState, HomeEffect> = when (mutation) {
        HomeMutation.PrimaryClicked -> ReduceOutcome.Changed(
            state = previous.copy(interactionCount = previous.interactionCount + 1)
        )
    }
}
