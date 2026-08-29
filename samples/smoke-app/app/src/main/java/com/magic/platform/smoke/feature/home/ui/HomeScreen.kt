package com.magic.platform.smoke.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magic.mvicore.android.compose.collectStateAsStateWithLifecycle
import com.magic.platform.smoke.R
import com.magic.platform.smoke.feature.home.contract.HomeIntent
import com.magic.platform.smoke.feature.home.contract.HomeState
import com.magic.platform.smoke.feature.home.presentation.HomeViewModel

@Composable
fun HomeRoute(viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.collectStateAsStateWithLifecycle(LocalLifecycleOwner.current)
    HomeScreen(
        state = state,
        onIntent = viewModel::onIntent,
    )
}

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(
            16.dp,
            Alignment.CenterVertically,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.platform_ready))
        Text(
            text = stringResource(
                R.string.interaction_count,
                state.interactionCount,
            )
        )
        Button(onClick = { onIntent(HomeIntent.OnPrimaryClick) }) {
            Text(text = stringResource(R.string.primary_action))
        }
    }
}
