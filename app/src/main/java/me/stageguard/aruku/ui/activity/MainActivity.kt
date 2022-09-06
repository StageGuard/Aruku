package me.stageguard.aruku.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.MutableLiveData
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.stageguard.aruku.service.ArukuMiraiService
import me.stageguard.aruku.service.IBotListObserver
import me.stageguard.aruku.ui.LocalArukuMiraiInterface
import me.stageguard.aruku.ui.LocalNavController
import me.stageguard.aruku.ui.page.login.LoginPage
import me.stageguard.aruku.ui.page.MessagePage
import me.stageguard.aruku.ui.page.ServiceConnectingPage
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.weakReference

const val NAV_MESSAGE = "message"
const val NAV_LOGIN = "login"

class MainActivity : ComponentActivity() {
    companion object {
        val unitProp = Unit
    }

    private val serviceConnector by lazy { ArukuMiraiService.Connector(this) }
    private val serviceInterface
        get() =
            if (serviceConnector.connected.value == true)
                serviceConnector.getValue(Unit, ::unitProp)
            else null

    private val botLst by lazy { MutableLiveData(listOf<Long>()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(serviceConnector)

        serviceConnector.connected.observe(this) { connected ->
            if (connected) {
                serviceInterface?.addBotListObserver(toString(), object : IBotListObserver.Stub() {
                    override fun onChange(newList: LongArray?) {
                        botLst.value = newList?.toList() ?: listOf()
                    }
                })
            } else {
                serviceInterface?.removeBotListObserver(toString())
            }
        }

        setContent {
            val serviceConnected = serviceConnector.connected.observeAsState(false)
            ArukuTheme {
                if (serviceConnected.value) {
                    Navigation()
                } else {
                    ServiceConnectingPage(serviceConnector.weakReference())
                }
            }
        }
    }


    @Composable
    fun Navigation() {
        val navController = rememberNavController()
        val botList = botLst.observeAsState(listOf())
        CompositionLocalProvider(
            LocalArukuMiraiInterface provides serviceInterface!!,
            LocalNavController provides navController
        ) {
            NavHost(navController, startDestination = NAV_MESSAGE) {
                composable(NAV_MESSAGE) {
                    MessagePage(botList, navigateToLoginPage = {
                        navController.navigate(NAV_LOGIN)
                    })
                }
                composable(NAV_LOGIN) {
                    LoginPage(onLoginSuccess = { navController.popBackStack() })
                }
            }
        }
    }
}
