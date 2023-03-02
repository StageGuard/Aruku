package me.stageguard.aruku.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.heyanle.okkv2.core.OkkvValue
import com.heyanle.okkv2.core.okkv
import loli.ball.okkv2.OkkvComposeInterceptor
import me.stageguard.aruku.ui.LocalOkkvProvider

@Composable
inline fun <reified T : Any> rememberOkkvNullable(key: String): OkkvValue<T> {
    val okkvImpl = LocalOkkvProvider.current
    return remember { okkvImpl.okkv(key) }
}

@Composable
fun <T : Any> OkkvValue<T>.observeAsState(): State<T?> {
    val state = remember { mutableStateOf(get()) }
    DisposableEffect(this) {
        OkkvComposeInterceptor.addListener(state, key(), okkv()) {
            @Suppress("UNCHECKED_CAST")
            state.value = it as? T
        }
        onDispose { OkkvComposeInterceptor.removeListener(state) }
    }
    return state
}