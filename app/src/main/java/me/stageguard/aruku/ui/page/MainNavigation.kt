package me.stageguard.aruku.ui.page

import android.util.Log
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import me.stageguard.aruku.preference.ArukuPreference
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.LocalNavController
import me.stageguard.aruku.ui.page.chat.ChatPage
import me.stageguard.aruku.ui.page.home.HomePage
import me.stageguard.aruku.ui.page.login.LoginPage
import me.stageguard.aruku.util.toLogTag

/**
 * Created by LoliBall on 2023/1/1 19:32.
 * https://github.com/WhichWho
 */
const val NAV_HOME = "home"
const val NAV_LOGIN = "login"
const val NAV_CHAT = "chat"

@Composable
fun Navigation() {

    val activeBot = remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        Log.i(toLogTag("Navigation"), "setActiveBot ${ArukuPreference.activeBot}")
        activeBot.value = ArukuPreference.activeBot
    }

    val navController = rememberNavController()
    CompositionLocalProvider(LocalNavController provides navController) {
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
            composable(
                route = "$NAV_CHAT/{target}",
                arguments = listOf(navArgument("target") { type = NavType.StringType }),
            ) {
                val target = it.arguments?.getString("target")?.toIntOrNull() ?: -1
                ChatPage(target)
            }
        }
        LaunchedEffect(Unit) {
            delay(3000)
            navController.navigate("$NAV_CHAT/123")
        }
    }
}