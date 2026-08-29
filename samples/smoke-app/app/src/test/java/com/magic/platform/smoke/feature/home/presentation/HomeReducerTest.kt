package com.magic.platform.smoke.feature.home.presentation

import com.magic.mvicore.contract.ReduceOutcome
import com.magic.platform.smoke.feature.home.contract.HomeMutation
import com.magic.platform.smoke.feature.home.contract.HomeState
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeReducerTest {
    @Test
    fun primaryClickIncrementsInteractionCount() {
        val next = HomeReducer.reduce(
            previous = HomeState(interactionCount = 2),
            mutation = HomeMutation.PrimaryClicked,
        )

        val changed = next as ReduceOutcome.Changed
        assertEquals(3, changed.state.interactionCount)
    }
}
