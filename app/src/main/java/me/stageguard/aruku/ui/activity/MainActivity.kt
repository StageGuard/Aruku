package me.stageguard.aruku.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.view.WindowCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import me.stageguard.aruku.service.ArukuServiceConnector
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.ui.LocalArukuMiraiInterface
import me.stageguard.aruku.ui.LocalStringLocale
import me.stageguard.aruku.ui.LocalSystemUiController
import me.stageguard.aruku.ui.common.MoeSnackBar
import me.stageguard.aruku.ui.page.Navigation
import me.stageguard.aruku.ui.page.ServiceConnectingPage
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.StringLocale
import me.stageguard.aruku.util.weakReference
import org.koin.android.ext.android.inject

val unitProp = Unit

class MainActivity : ComponentActivity() {

    private val serviceConnector: ArukuServiceConnector by inject()
    private val serviceInterface: IArukuMiraiInterface by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(serviceConnector)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val serviceConnected = serviceConnector.connected.observeAsState(false)
            val systemUiController = rememberSystemUiController(window)
            ArukuTheme {
                val focusManager = LocalFocusManager.current
                systemUiController.setStatusBarColor(
                    Color.Transparent,
                    MaterialTheme.colors.isLight
                )
                Box(
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { focusManager.clearFocus() })
                ) {
                    CompositionLocalProvider(
                        LocalStringLocale provides StringLocale(this@MainActivity),
                        LocalSystemUiController provides systemUiController
                    ) {
                        if (serviceConnected.value) {
                            CompositionLocalProvider(LocalArukuMiraiInterface provides serviceInterface) {
                                Navigation()
                            }
                        } else {
                            ServiceConnectingPage(serviceConnector.weakReference())
                        }
                    }
                    MoeSnackBar(Modifier.statusBarsPadding())
                }
            }
        }
    }

    override fun onDestroy() {
        lifecycle.removeObserver(serviceConnector)
        super.onDestroy()
    }

}
