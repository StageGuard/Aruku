package me.stageguard.aruku.ui.page.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.with
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import me.stageguard.aruku.ui.LocalArukuMiraiInterface
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.page.login.CaptchaRequired
import me.stageguard.aruku.ui.page.login.LoginState
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringResC

@Composable
fun HomePage(
    botList: SnapshotStateList<Long>,
    navigateToLoginPage: () -> Unit,
    onSwitchAccount: (Long) -> Unit
) {
    val arukuServiceInterface = LocalArukuMiraiInterface.current
    val bot = LocalBot.current
    val viewModel: HomeViewModel = viewModel { HomeViewModel(arukuServiceInterface, botList) }
    SideEffect {
        bot?.let { viewModel.observeAccountState(it) }
    }
    HomeView(
        viewModel.getAccountBasicInfo(),
        viewModel.accountState,
        navigateToLoginPage,
        onSwitchAccount = onSwitchAccount,
        onRetryCaptchaClick = { accountNo -> viewModel.submitCaptcha(accountNo, null) },
        onSubmitCaptchaClick = { accountNo, result -> viewModel.submitCaptcha(accountNo, result) },
        onLoginFailedClick = { viewModel.loginFailed(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeView(
    botList: List<BasicAccountInfo>,
    state: State<AccountState>,
    navigateToLoginPage: () -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onRetryCaptchaClick: (Long) -> Unit,
    onSubmitCaptchaClick: (Long, String) -> Unit,
    onLoginFailedClick: (Long) -> Unit

) {
    val botListExpanded = remember { mutableStateOf(false) }
    val currNavPage = remember(HomeNavSelection.MESSAGE) {
        mutableStateOf(homeNavs[HomeNavSelection.MESSAGE]!!)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            HomeTopAppBar(
                botList = botList,
                botListExpanded = botListExpanded,
                title = currNavPage.value.label.stringResC,
                modifier = Modifier,
                onAvatarClick = {
                    if (botList.isNotEmpty()) {
                        botListExpanded.value = !botListExpanded.value
                    } else navigateToLoginPage()
                },
                onSwitchAccount = onSwitchAccount,
                onAddAccountClick = navigateToLoginPage
            )
        },
        bottomBar = {
            HomeNavigationBar(currNavPage.value.selection) { prev, curr ->
                currNavPage.value = homeNavs[curr]!!
            }
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
            targetState.value.composable(padding)
        }
    }

    if (state.value is AccountState.Login) {
        val loginState = state.value as AccountState.Login
        if (loginState.state is LoginState.CaptchaRequired) {
            CaptchaRequired(
                state = loginState.state,
                onRetryCaptchaClick = onRetryCaptchaClick,
                onSubmitCaptchaClick = onSubmitCaptchaClick,
                onLoginFailedClick = onLoginFailedClick,
            )
        }
    }

}


@Preview
@Composable
fun HomeiewPreview() {
    val list = remember { mutableStateListOf<BasicAccountInfo>() }
    val state = remember { mutableStateOf(AccountState.Default) }
    ArukuTheme {
        HomeView(
            botList = list,
            state = state,
            {}, {}, {}, { _, _ -> }, {},
        )
    }
}