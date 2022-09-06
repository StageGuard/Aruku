package me.stageguard.aruku.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.MutableLiveData
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.stageguard.aruku.service.ArukuMiraiService
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.service.IBotListObserver
import me.stageguard.aruku.ui.LocalArukuMiraiInterface
import me.stageguard.aruku.ui.page.LoginPage
import me.stageguard.aruku.ui.page.MessagePage
import me.stageguard.aruku.ui.page.ServiceConnectingPage
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.weakReference

class MainActivity : ComponentActivity() {
    companion object { val unitProp = Unit }
    private val serviceConnector by lazy { ArukuMiraiService.Connector(this) }
    private val serviceInterface get() =
        if (serviceConnector.connected.value == true) serviceConnector.getValue(Unit, ::unitProp) else null

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
            val navController = rememberNavController()
            val serviceConnected = serviceConnector.connected.observeAsState(false)
            val botList = botLst.observeAsState(listOf())
            ArukuTheme {
                if (serviceConnected.value) {
                    CompositionLocalProvider(LocalArukuMiraiInterface provides serviceInterface!!) {
                        NavHost(navController, startDestination = "message") {
                            composable("message") {
                                MessagePage(botList, navigateToLoginPage = {
                                    navController.navigate("login")
                                })
                            }
                            composable("login") {
                                LoginPage(onLoginSuccess = { navController.popBackStack() })
                            }
                        }
                    }
                } else {
                    ServiceConnectingPage(serviceConnector.weakReference())
                }
            }
        }
    }
}