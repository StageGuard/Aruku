package me.stageguard.aruku.ui.common

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

/**
 * Created by LoliBall on 2022/9/6 18:27.
 * https://github.com/WhichWho
 */

@Composable
fun ExpandedAnimatedVisibility(
    expanded: Boolean,
    content: @Composable (AnimatedVisibilityScope.() -> Unit)
) {
    AnimatedVisibility(
        expanded,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) { content() }
}