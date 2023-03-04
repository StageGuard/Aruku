package me.stageguard.aruku.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.view.WindowCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.ServiceConnector
import me.stageguard.aruku.ui.LocalStringLocale
import me.stageguard.aruku.ui.LocalSystemUiController
import me.stageguard.aruku.ui.common.MoeSnackBar
import me.stageguard.aruku.ui.page.MainPage
import me.stageguard.aruku.ui.page.ServiceConnectingPage
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.StringLocale
import me.stageguard.aruku.util.weakReference
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf

class MainActivity : ComponentActivity() {

    private val serviceConnector: ServiceConnector = ServiceConnector(this)

    init {
        // ensure the main repository singleton is created.
        inject<MainRepository> { parametersOf(serviceConnector.weakReference()) }.value
        lifecycle.addObserver(serviceConnector)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val focusManager = LocalFocusManager.current
            val systemUiController = rememberSystemUiController(window)
            val serviceConnected = serviceConnector.connected.observeAsState(false)
            val isDarkTheme = isSystemInDarkTheme()
            ArukuTheme {
                SideEffect {
                    systemUiController.setStatusBarColor(Color.Transparent, isDarkTheme)
                }
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
                            MainPage(serviceConnector.bots)
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
