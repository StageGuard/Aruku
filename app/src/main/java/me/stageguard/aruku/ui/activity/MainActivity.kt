package me.stageguard.aruku.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.MutableLiveData
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.stageguard.aruku.service.ArukuMiraiService
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.service.observeBotList
import me.stageguard.aruku.ui.LocalArukuMiraiInterface
import me.stageguard.aruku.ui.page.LoginPage
import me.stageguard.aruku.ui.page.MessagePage
import me.stageguard.aruku.ui.page.ServiceConnectingPage
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.weakReference

class MainActivity : ComponentActivity() {
    private val serviceConnector by lazy { ArukuMiraiService.Connector(this) }
    private val serviceInterface: IArukuMiraiInterface by serviceConnector
    private val botLst by lazy { MutableLiveData(serviceInterface.bots.toList()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(serviceConnector)

        serviceConnector.connected.observe(this) { connected ->
            if (connected) serviceInterface.observeBotList(this) { botLst.value = it }
        }

        setContent {
            val navController = rememberNavController()
            val serviceConnected = serviceConnector.connected.observeAsState(false)
            ArukuTheme {
                if (serviceConnected.value) {
                    CompositionLocalProvider(LocalArukuMiraiInterface provides serviceInterface) {
                        NavHost(navController, startDestination = "messages") {
                            composable("messages") {
                                val botList = botLst.observeAsState(listOf())
                                MessagePage(botList, navigateToLoginPage = {
                                    navController.navigate("login")
                                })
                            }
                            composable("login") {
                                LoginPage(onLoginSuccess = {
                                    navController.navigate("messages") {
                                        popUpTo("messages") { inclusive = true }
                                    }
                                })
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