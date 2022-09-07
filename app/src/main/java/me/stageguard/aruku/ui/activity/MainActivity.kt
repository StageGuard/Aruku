package me.stageguard.aruku.ui.activity

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
import me.stageguard.aruku.preference.accountStore
import me.stageguard.aruku.service.ArukuMiraiService
import me.stageguard.aruku.service.IBotListObserver
import me.stageguard.aruku.ui.LocalArukuMiraiInterface
import me.stageguard.aruku.ui.LocalNavController
import me.stageguard.aruku.ui.page.ServiceConnectingPage
import me.stageguard.aruku.ui.page.login.LoginPage
import me.stageguard.aruku.ui.page.message.MessagePage
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.toLogTag
import me.stageguard.aruku.util.weakReference

val unitProp = Unit

class MainActivity : ComponentActivity() {
    companion object {
        const val NAV_MESSAGE = "message"
        const val NAV_LOGIN = "login"
    }

    private val serviceConnector by lazy { ArukuMiraiService.Connector(this) }
    private val serviceInterface
        get() =
            if (serviceConnector.connected.value == true)
                serviceConnector.getValue(Unit, ::unitProp)
            else null

    private val botList by lazy { MutableLiveData(listOf<Long>()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(serviceConnector)

        serviceConnector.connected.observe(this) { connected ->
            if (connected) {
                botList.value = serviceInterface?.bots?.toList() ?: listOf()
                serviceInterface?.addBotListObserver(toString(), object : IBotListObserver.Stub() {
                    override fun onChange(newList: LongArray?) {
                        botList.postValue(newList?.toList() ?: listOf())
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
        val botList = botList.observeAsState(listOf())
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
                    LoginPage(onLoginSuccess = { accountInfo ->
                        lifecycleScope.launch {
                            Log.i(toLogTag(), "updating accountStore")
                            accountStore.updateData { accounts ->
                                accounts.toBuilder().putAccount(accountInfo.accountNo, accountInfo).build()
                            }
                            Log.i(toLogTag(), accountStore.data.single().accountMap.toString())
                        }
                        navController.popBackStack()
                    })
                }
            }
        }
    }
}
