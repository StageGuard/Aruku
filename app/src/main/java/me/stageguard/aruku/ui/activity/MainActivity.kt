package me.stageguard.aruku.ui.activity

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.stageguard.aruku.database.ArukuDatabase
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
import me.stageguard.aruku.util.toLogTag
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
    private val database: ArukuDatabase by inject()

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
                            onSwitchAccount = { activeBot.value = Bot.getInstanceOrNull(it) }
                        )
                    }
                }
                composable(NAV_LOGIN) {
                    LoginPage(onLoginSuccess = { accountInfo ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            Log.i(toLogTag(), "updating accountStore")

                            database.accounts().insert(accountInfo.into())
                            ArukuPreference.activeBot = accountInfo.accountNo
                            activeBot.value = Bot.getInstance(accountInfo.accountNo)
                        }
                        navController.popBackStack()
                    })
                }
            }
        }
    }
}
