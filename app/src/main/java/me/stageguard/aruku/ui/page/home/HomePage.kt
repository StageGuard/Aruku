package me.stageguard.aruku.ui.page.home

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.with
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.LocalHomeAccountState
import me.stageguard.aruku.ui.LocalNavController
import me.stageguard.aruku.ui.page.NAV_LOGIN
import me.stageguard.aruku.ui.page.login.CaptchaRequired
import me.stageguard.aruku.ui.page.login.LoginState
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringResC
import me.stageguard.aruku.util.tag
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomePage(
    onSwitchAccount: (Long) -> Unit,
    onLaunchLoginSuccess: (Long) -> Unit
) {
    val bot = LocalBot.current
    val navController = LocalNavController.current
    val lifecycle = LocalLifecycleOwner.current
    val viewModel: HomeViewModel = koinViewModel()

    LaunchedEffect(true) {
        viewModel.observeAccountList(lifecycle)
    }

    val coroutineScope = rememberCoroutineScope()
    val accountState by viewModel.loginState.collectAsState(coroutineScope.coroutineContext)
    val accounts = viewModel.accounts.collectAsState(listOf(), coroutineScope.coroutineContext)
    val rOnSwitchAccount by rememberUpdatedState(onSwitchAccount)
    val rOnLaunchLoginSuccess by rememberUpdatedState(onLaunchLoginSuccess)

    LaunchedEffect(bot) {
        viewModel.observeAccountState(bot)
    }
    LaunchedEffect(accountState) {
        Log.i(tag("HomePage"), "current account state: $accountState")
        if (accountState is AccountState.Online) rOnLaunchLoginSuccess(accountState.bot)
    }

    CompositionLocalProvider(LocalHomeAccountState provides accountState) {
        HomeView(
            currentNavSelection = viewModel.currentNavSelection,
            botList = accounts,
            navigateToLoginPage = { navController.navigate(NAV_LOGIN) },
            onSwitchAccount = rOnSwitchAccount,
            onRetryCaptcha = { accountNo -> viewModel.submitCaptcha(accountNo, null) },
            onSubmitCaptcha = { accountNo, result -> viewModel.submitCaptcha(accountNo, result) },
            onCancelLogin = { viewModel.cancelLogin(it) },
            onHomeNavigate = { _, curr -> viewModel.currentNavSelection.value = homeNaves[curr]!! }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
private fun HomeView(
    currentNavSelection: State<HomeNav>,
    botList: State<List<BasicAccountInfo>>,
    navigateToLoginPage: () -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onRetryCaptcha: (Long) -> Unit,
    onSubmitCaptcha: (Long, String) -> Unit,
    onCancelLogin: (Long) -> Unit,
    onHomeNavigate: (HomeNavSelection, HomeNavSelection) -> Unit
) {
    val currNavPage = remember(HomeNavSelection.MESSAGE) { currentNavSelection }
    val state = LocalHomeAccountState.current
//    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            HomeTopAppBar(
                botList = botList,
                title = currNavPage.value.label.stringResC,
                modifier = Modifier,
                onSwitchAccount = onSwitchAccount,
                onAddAccount = navigateToLoginPage,
            )
        },
        bottomBar = {
            HomeNavigationBar(currNavPage.value.selection, onHomeNavigate)
        }
    ) { padding ->
        AnimatedContent(
            targetState = currNavPage,
            transitionSpec = trans@{
                val spec = tween<IntOffset>(500)
                val direction =
                    if (targetState.value.selection.id > initialState.value.selection.id) {
                        AnimatedContentScope.SlideDirection.Left
                    } else {
                        AnimatedContentScope.SlideDirection.Right
                    }
                return@trans slideIntoContainer(direction, spec) with slideOutOfContainer(
                    direction,
                    spec
                )
            }
        ) { targetState ->
            targetState.value.content(padding)
        }
    }

    if (state is AccountState.Login) {
        if (state.state is LoginState.CaptchaRequired) {
            CaptchaRequired(
                state = state.state,
                onRetryCaptcha = onRetryCaptcha,
                onSubmitCaptcha = onSubmitCaptcha,
                onCancelLogin = onCancelLogin,
            )
        }
    }

}


@Preview
@Composable
fun HomeViewPreview() {
    val list = remember { mutableStateOf(listOf<BasicAccountInfo>()) }
    val state by remember { mutableStateOf(AccountState.Default) }
    val navState = remember { mutableStateOf(homeNaves[HomeNavSelection.MESSAGE]!!) }
    ArukuTheme {
        HomeView(
            navState, botList = list,
            {}, {}, {}, { _, _ -> }, {}, { _, _ -> },
        )
    }
}