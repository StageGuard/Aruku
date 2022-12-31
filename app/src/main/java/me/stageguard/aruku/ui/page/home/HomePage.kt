package me.stageguard.aruku.ui.page.home

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.with
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.page.login.CaptchaRequired
import me.stageguard.aruku.ui.page.login.LoginState
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringResC
import me.stageguard.aruku.util.toLogTag
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomePage(
    navigateToLoginPage: () -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onLaunchLoginSuccess: (Long) -> Unit
) {
    val bot = LocalBot.current
    val viewModel: HomeViewModel = koinViewModel()
    val state = viewModel.loginState.value

    LaunchedEffect(bot) {
        Log.i(toLogTag(), "observeAccountState $bot")
        viewModel.observeAccountState(bot)
    }
    LaunchedEffect(state) {
        if (state is AccountState.Online) onLaunchLoginSuccess(state.bot)
    }

    HomeView(
        viewModel.currentNavSelection,
        viewModel.getAccountBasicInfo(),
        viewModel.loginState,
        navigateToLoginPage,
        onSwitchAccount = onSwitchAccount,
        onRetryCaptcha = { accountNo -> viewModel.submitCaptcha(accountNo, null) },
        onSubmitCaptcha = { accountNo, result -> viewModel.submitCaptcha(accountNo, result) },
        onCancelLogin = { viewModel.cancelLogin(it) },
        onHomeNavigate = { _, curr -> viewModel.currentNavSelection.value = homeNaves[curr]!! }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeView(
    currentNavSelection: State<HomeNav>,
    botList: List<BasicAccountInfo>,
    state: State<AccountState>,
    navigateToLoginPage: () -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onRetryCaptcha: (Long) -> Unit,
    onSubmitCaptcha: (Long, String) -> Unit,
    onCancelLogin: (Long) -> Unit,
    onHomeNavigate: (HomeNavSelection, HomeNavSelection) -> Unit

) {
    val botListExpanded = remember { mutableStateOf(false) }
    val currNavPage = remember(HomeNavSelection.MESSAGE) { currentNavSelection }
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            HomeTopAppBar(
                botList = botList,
                botListExpanded = botListExpanded,
                showAvatarProgressIndicator = state.value is AccountState.Login,
                activeAccountOnline = state.value is AccountState.Online,
                title = currNavPage.value.label.stringResC,
                modifier = Modifier,
                onAvatarClick = {
                    if (botList.isNotEmpty()) {
                        botListExpanded.value = !botListExpanded.value
                    } else navigateToLoginPage()
                },
                onSwitchAccount = onSwitchAccount,
                onAddAccountClick = navigateToLoginPage,
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
                val direction = if (targetState.value.selection.id > initialState.value.selection.id) {
                    AnimatedContentScope.SlideDirection.Left
                } else {
                    AnimatedContentScope.SlideDirection.Right
                }
                return@trans slideIntoContainer(direction, spec) with slideOutOfContainer(direction, spec)
            }
        ) { targetState ->
            targetState.value.content(padding)
        }
    }

    if (state.value is AccountState.Login) {
        val loginState = state.value as AccountState.Login
        if (loginState.state is LoginState.CaptchaRequired) {
            CaptchaRequired(
                state = loginState.state,
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
    val list = remember { mutableStateListOf<BasicAccountInfo>() }
    val state = remember { mutableStateOf(AccountState.Default) }
    val navState = remember { mutableStateOf(homeNaves[HomeNavSelection.MESSAGE]!!) }
    ArukuTheme {
        HomeView(
            navState, botList = list, state = state,
            {}, {}, {}, { _, _ -> }, {}, { _, _ -> },
        )
    }
}