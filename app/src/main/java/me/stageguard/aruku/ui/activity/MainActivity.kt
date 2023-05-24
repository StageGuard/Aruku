package me.stageguard.aruku.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.view.WindowCompat
import androidx.lifecycle.flowWithLifecycle
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.cancellable
import me.stageguard.aruku.MainRepositoryImpl
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.ServiceConnector
import me.stageguard.aruku.ui.LocalStringLocale
import me.stageguard.aruku.ui.LocalSystemUiController
import me.stageguard.aruku.ui.common.MoeSnackBar
import me.stageguard.aruku.ui.page.MainPage
import me.stageguard.aruku.ui.page.ServiceConnectingPage
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.StringLocale
import me.stageguard.aruku.util.createAndroidLogger
import me.stageguard.aruku.util.weakReference
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val logger = createAndroidLogger()
    private val serviceConnector: ServiceConnector = ServiceConnector(this)
    private val repo by inject<MainRepository>(mode = LazyThreadSafetyMode.SYNCHRONIZED)

    private val stateFlow by lazy { repo.stateFlow.cancellable().flowWithLifecycle(lifecycle) }

    init {
        logger.i("initializing main activity.")
        lifecycle.addObserver(serviceConnector)
        lifecycle.addObserver(repo as MainRepositoryImpl)

        repo.referConnector(serviceConnector)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val focusManager = LocalFocusManager.current
            val systemUiController = rememberSystemUiController(window)
            val serviceConnected = serviceConnector.connected.observeAsState(false)
            ArukuTheme(dynamicColor = false) {
                Box(
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { focusManager.clearFocus() }
                    )
                ) {
                    CompositionLocalProvider(
                        LocalStringLocale provides StringLocale(this@MainActivity),
                        LocalSystemUiController provides systemUiController
                    ) {
                        if (serviceConnected.value) {
                            MainPage(stateFlow)
                        } else {
                            ServiceConnectingPage(serviceConnector.weakReference())
                        }
                    }
                    MoeSnackBar(Modifier.statusBarsPadding())
                }
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onDestroy() {
        lifecycle.removeObserver(serviceConnector)
        super.onDestroy()
    }

}
