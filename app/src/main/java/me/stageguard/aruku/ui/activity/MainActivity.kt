package me.stageguard.aruku.ui.activity

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import me.stageguard.aruku.preference.ArukuPreference
import me.stageguard.aruku.service.ArukuMiraiService
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.ui.*
import me.stageguard.aruku.ui.page.ServiceConnectingPage
import me.stageguard.aruku.ui.page.home.HomePage
import me.stageguard.aruku.ui.page.login.LoginPage
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.StringLocale
import me.stageguard.aruku.util.toLogTag
import me.stageguard.aruku.util.weakReference
import org.koin.android.ext.android.inject

val unitProp = Unit

class MainActivity : ComponentActivity() {
    companion object {
        const val NAV_HOME = "home"
        const val NAV_LOGIN = "login"
    }

    private val serviceConnector: ArukuMiraiService.Connector by inject()
    private val serviceInterface: IArukuMiraiInterface by inject()

    private val activeBot = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(serviceConnector)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        lifecycleScope.launchWhenCreated {
            Log.i(toLogTag(), "setActiveBot ${ArukuPreference.activeBot}")
            activeBot.value = ArukuPreference.activeBot
        }

        setContent {
            val serviceConnected = serviceConnector.connected.observeAsState(false)
            val systemUiController = rememberSystemUiController(window)
            ArukuTheme {
                CompositionLocalProvider(
                    LocalStringLocale provides StringLocale(this),
                    LocalSystemUiController provides systemUiController
                ) {
                    if (serviceConnected.value) {
                        Navigation()
                    } else {
                        ServiceConnectingPage(serviceConnector.weakReference())
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        lifecycle.removeObserver(serviceConnector)
        super.onDestroy()
    }


    @Composable
    fun Navigation() {
        val navController = rememberNavController()
        CompositionLocalProvider(
            LocalArukuMiraiInterface provides serviceInterface,
            LocalMainNavProvider provides navController,
        ) {
            NavHost(navController, startDestination = NAV_HOME) {
                composable(NAV_HOME) {
                    CompositionLocalProvider(LocalBot provides activeBot.value) {
                        HomePage(
                            navigateToLoginPage = { navController.navigate(NAV_LOGIN) },
                            onSwitchAccount = { accountNo ->
                                ArukuPreference.activeBot = accountNo
                                activeBot.value = accountNo
                            },
                            onLaunchLoginSuccess = { accountNo ->
                                if (accountNo == ArukuPreference.activeBot) {
                                    activeBot.value = accountNo
                                }
                            }
                        )
                    }
                }
                composable(NAV_LOGIN) {
                    LoginPage(onLoginSuccess = { accountNo ->
                        ArukuPreference.activeBot = accountNo
                        activeBot.value = accountNo
                        navController.popBackStack()
                    })
                }
            }
        }
    }
}
