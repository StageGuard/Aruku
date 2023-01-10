package me.stageguard.aruku.ui.page

import android.util.Log
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.*
import androidx.navigation.NavType.ParcelableType
import androidx.navigation.navArgument
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import me.stageguard.aruku.preference.ArukuPreference
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.LocalNavController
import me.stageguard.aruku.ui.common.animatedComposable
import me.stageguard.aruku.ui.page.chat.ChatPage
import me.stageguard.aruku.ui.page.home.HomePage
import me.stageguard.aruku.ui.page.login.LoginPage
import me.stageguard.aruku.util.tag

/**
 * Created by LoliBall on 2023/1/1 19:32.
 * https://github.com/WhichWho
 */
const val NAV_HOME = "home"
const val NAV_LOGIN = "login"
const val NAV_CHAT = "chat"

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun Navigation() {

    val activeBot = remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        Log.i(tag("Navigation"), "setActiveBot ${ArukuPreference.activeBot}")
        activeBot.value = ArukuPreference.activeBot
    }

    val navController = rememberAnimatedNavController()
    CompositionLocalProvider(
        LocalNavController provides navController,
        LocalBot provides activeBot.value
    ) {
        AnimatedNavHost(navController, startDestination = NAV_HOME) {
            animatedComposable(NAV_HOME) {
                HomePage(
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
            animatedComposable(NAV_LOGIN) {
                LoginPage(onLoginSuccess = { accountNo ->
                    ArukuPreference.activeBot = accountNo
                    activeBot.value = accountNo
                    navController.popBackStack()
                })
            }
            animatedComposable(
                route = "$NAV_CHAT/{contact}",
                arguments = listOf(navArgument("contact") {
                    type = ParcelableType(ArukuContact::class.java)
                    nullable = false
                }),
            ) { entry ->
                val arguments = entry.arguments ?: throw IllegalArgumentException("no bundle passed into chat page.")
                val contact = arguments.getParcelable("contact", ArukuContact::class.java)
                    ?: throw IllegalArgumentException("no contact info in bundle of chat page.")
                ChatPage(contact)
            }
        }
    }
}