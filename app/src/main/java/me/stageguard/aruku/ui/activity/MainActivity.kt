package me.stageguard.aruku.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.stageguard.aruku.preference.ArukuPreference
import me.stageguard.aruku.service.ArukuMiraiService
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.ui.LocalArukuMiraiInterface
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.LocalMainNavProvider
import me.stageguard.aruku.ui.LocalStringRes
import me.stageguard.aruku.ui.page.ServiceConnectingPage
import me.stageguard.aruku.ui.page.home.HomePage
import me.stageguard.aruku.ui.page.login.LoginPage
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.StringResource
import me.stageguard.aruku.util.weakReference
import net.mamoe.mirai.Bot
import org.koin.android.ext.android.inject

val unitProp = Unit

class MainActivity : ComponentActivity() {
    companion object {
        const val NAV_HOME = "home"
        const val NAV_LOGIN = "login"
    }

    private val serviceConnector: ArukuMiraiService.Connector by inject()
    private val serviceInterface: IArukuMiraiInterface by inject()

    private val activeBot = mutableStateOf<Bot?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(serviceConnector)

        setContent {
            val serviceConnected = serviceConnector.connected.observeAsState(false)
            ArukuTheme {
                CompositionLocalProvider(LocalStringRes provides StringResource(this)) {
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
        super.onDestroy()
        lifecycle.removeObserver(serviceConnector)
    }


    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun Navigation() {
        val navController = rememberNavController()
        CompositionLocalProvider(
            LocalArukuMiraiInterface provides serviceInterface,
            LocalMainNavProvider provides navController
        ) {
            NavHost(navController, startDestination = NAV_HOME) {
                composable(NAV_HOME) {
                    CompositionLocalProvider(LocalBot provides activeBot.value) {
                        HomePage(
                            navigateToLoginPage = { navController.navigate(NAV_LOGIN) },
                            onSwitchAccount = { accountNo ->
                                ArukuPreference.activeBot = accountNo
                                activeBot.value = Bot.getInstanceOrNull(accountNo)
                            },
                            onLaunchLoginSuccess = { accountNo ->
                                if (accountNo == ArukuPreference.activeBot) {
                                    activeBot.value = Bot.getInstance(accountNo)
                                }
                            }
                        )
                    }
                }
                composable(NAV_LOGIN) {
                    LoginPage(onLoginSuccess = { accountNo ->
                        ArukuPreference.activeBot = accountNo
                        activeBot.value = Bot.getInstance(accountNo)
                        navController.popBackStack()
                    })
                }
            }
        }
    }
}
