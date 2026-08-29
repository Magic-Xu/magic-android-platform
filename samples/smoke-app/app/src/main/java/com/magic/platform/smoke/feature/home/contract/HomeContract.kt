package com.magic.platform.smoke.feature.home.contract

import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.UiEffect

data class HomeState(
    val interactionCount: Int = 0,
) : MviState

sealed interface HomeIntent : MviUiIntent {
    data object OnPrimaryClick : HomeIntent
}

sealed interface HomeMutation : MviMutation {
    data object PrimaryClicked : HomeMutation
}

sealed interface HomeEffect : UiEffect {
    data object Reserved : HomeEffect
}
