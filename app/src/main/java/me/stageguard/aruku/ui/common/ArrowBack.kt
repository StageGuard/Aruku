package me.stageguard.aruku.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.stageguard.aruku.ui.LocalNavController

/**
 * Created by LoliBall on 2022/2/25 9:27.
 * https://github.com/WhichWho
 */
@Composable
fun ArrowBack(onClick: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val nav = LocalNavController.current
    IconButton(
        modifier = Modifier.padding(5.dp, 0.dp, 0.dp, 0.dp),
        onClick = {
            if (onClick == null) {
                scope.launch {
                    nav.popBackStack()
                }
            } else {
                onClick()
            }
        }) {
        Icon(Icons.Filled.ArrowBack, contentDescription = "back")
    }
}