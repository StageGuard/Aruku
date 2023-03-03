package me.stageguard.aruku.ui.page

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.*
import androidx.navigation.navArgument
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import me.stageguard.aruku.ui.LocalAccountState
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.LocalNavController
import me.stageguard.aruku.ui.common.animatedComposable
import me.stageguard.aruku.ui.common.observeAsState
import me.stageguard.aruku.ui.common.rememberArgument
import me.stageguard.aruku.ui.page.chat.ChatPage
import me.stageguard.aruku.ui.page.home.AccountState
import me.stageguard.aruku.ui.page.home.HomePage
import me.stageguard.aruku.ui.page.login.CaptchaRequired
import me.stageguard.aruku.ui.page.login.LoginPage
import me.stageguard.aruku.ui.page.login.LoginState
import org.koin.androidx.compose.koinViewModel

/**
 * Created by LoliBall on 2023/1/1 19:32.
 * https://github.com/WhichWho
 */
const val NAV_HOME = "home"
const val NAV_LOGIN = "login"
const val NAV_CHAT = "chat"

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainPage() {
    val viewModel: MainViewModel = koinViewModel()

    val activeBot by viewModel.activeAccountPref.observeAsState()
    val state by viewModel.accountState.collectAsState()
    LaunchedEffect(activeBot) {
        viewModel.observeAccountState(activeBot)
    }
    LaunchedEffect(state) {
        if (state is AccountState.Online) {
            viewModel.activeAccountPref.set(activeBot)
        }
    }
    val navController = rememberAnimatedNavController()

    CompositionLocalProvider(
        LocalBot provides activeBot,
        LocalAccountState provides state,
        LocalNavController provides navController
    ) {
        AnimatedNavHost(navController, startDestination = NAV_HOME) {
            animatedComposable(NAV_HOME) {
                HomePage(
                    onSwitchAccount = { accountNo -> viewModel.activeAccountPref.set(accountNo) },
                    onLogout = { viewModel.doLogout(it) },
                    onLogin = { viewModel.doLogin(it) }
                )
            }
            animatedComposable(NAV_LOGIN) {
                LoginPage(onLoginSuccess = { accountNo ->
                    viewModel.activeAccountPref.set(accountNo)
                    navController.popBackStack()
                })
            }
            animatedComposable(
                route = "$NAV_CHAT/{contact}",
                arguments = listOf(navArgument("contact") {
                    type = ChatPageNavType
                    nullable = false
                }),
            ) { entry ->
                val contact = entry.rememberArgument<ChatPageNav>("contact")
                    ?: throw IllegalArgumentException("no contact info in bundle of chat page.")

                ChatPage(contact)
            }
        }

        when (val loginState = state) {
            is AccountState.Login -> if (loginState.state is LoginState.CaptchaRequired) {
                CaptchaRequired(
                    state = loginState.state,
                    onRetryCaptcha = { accountNo -> viewModel.submitCaptcha(accountNo, null) },
                    onSubmitCaptcha = { accountNo, result ->
                        viewModel.submitCaptcha(
                            accountNo,
                            result
                        )
                    },
                    onCancelLogin = { viewModel.cancelLogin(it) },
                )
            }

            else -> {}
        }
    }
}