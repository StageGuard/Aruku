package me.stageguard.aruku.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Created by LoliBall on 2022/2/9 0:05.
 * https://github.com/WhichWho
 */
@Composable
fun FastScrollToTopFab(
    listState: LazyListState,
    after: Int = 10,
    padding: PaddingValues = PaddingValues(0.dp)
) {
    val scope = rememberCoroutineScope()
    FastScrollToTopFab(
        state = remember { derivedStateOf { listState.firstVisibleItemIndex > after } },
        padding = padding
    ) {
        scope.launch {
            listState.animateScrollToItem(0, 0)
        }
    }
}

@Composable
fun FastScrollToTopFab(
    listState: LazyGridState,
    after: Int = 10,
    padding: PaddingValues = PaddingValues(0.dp)
) {
    val scope = rememberCoroutineScope()
    FastScrollToTopFab(
        state = remember { derivedStateOf { listState.firstVisibleItemIndex > after } },
        padding = padding
    ) {
        scope.launch {
            listState.animateScrollToItem(0, 0)
        }
    }
}

@Composable
fun FastScrollToTopFab(
    state: State<Boolean>,
    padding: PaddingValues = PaddingValues(0.dp),
    onClick: () -> Unit
) {
    AnimatedVisibility(
        visible = state.value,
        enter = slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight + 200 },
            animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing)
        ),
        exit = slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight + 200 },
            animationSpec = tween(durationMillis = 250, easing = FastOutLinearInEasing)
        )
    ) {
        Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.fillMaxSize()) {
            FloatingActionButton(
                onClick = {
                    onClick()
                },
                modifier = Modifier
                    .padding(20.dp)
                    .padding(padding)
            ) {
                Image(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = null
                )
            }
        }
    }
}