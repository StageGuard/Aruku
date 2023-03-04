package me.stageguard.aruku.ui.page.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.with
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.stageguard.aruku.ui.LocalAccountsState
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.LocalNavController
import me.stageguard.aruku.ui.LocalSystemUiController
import me.stageguard.aruku.ui.page.AccountState
import me.stageguard.aruku.ui.page.NAV_LOGIN
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringResC
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomePage(
    onSwitchAccount: (Long) -> Unit,
    onLogin: (Long) -> Unit,
    onLogout: (Long) -> Unit,
) {
    val bot = LocalBot.current
    val navController = LocalNavController.current
    val accountsState = LocalAccountsState.current

    val viewModel: HomeViewModel = koinViewModel()
    val accounts by viewModel.accounts.collectAsState()

    LaunchedEffect(accountsState) {
        viewModel.updateAccounts(accountsState)
    }

    HomeView(
        currentNavSelection = viewModel.currentNavSelection,
        state = accountsState[bot] ?: AccountState.Default,
        accounts = accounts,
        onNavigateToLoginPage = { navController.navigate(NAV_LOGIN) },
        onSwitchAccount = onSwitchAccount,
        onLogin = onLogin,
        onLogout = onLogout,
        onHomeNavigate = { _, curr -> viewModel.currentNavSelection.value = homeNaves[curr]!! }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
private fun HomeView(
    currentNavSelection: State<HomeNav>,
    state: AccountState,
    accounts: List<BasicAccountInfo>,
    onNavigateToLoginPage: () -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onLogin: (Long) -> Unit,
    onLogout: (Long) -> Unit,
    onHomeNavigate: (HomeNavSelection, HomeNavSelection) -> Unit
) {
    val bot = LocalBot.current
    val systemUiController = LocalSystemUiController.current

    val backgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    val navigationContainerColor =
        MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp).copy(alpha = 0.95f)
    val topAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = backgroundColor,
        scrolledContainerColor = navigationContainerColor
    )
    val scrollState = TopAppBarDefaults.pinnedScrollBehavior()

    val currNavPage by remember(HomeNavSelection.MESSAGE) { currentNavSelection }
    val currAccount = bot?.let { id -> accounts.find { it.id == id } }
    val showAccountDialog = remember { mutableStateOf(false) }

    SideEffect {
        systemUiController.setNavigationBarColor(navigationContainerColor.copy(0.13f))
        systemUiController.setStatusBarColor(backgroundColor.copy(0.13f))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = backgroundColor,
            modifier = Modifier.fillMaxSize()
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollState.nestedScrollConnection),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                topBar = {
                    HomeTopAppBar(
                        title = currNavPage.label.stringResC,
                        account = currAccount,
                        barColors = topAppBarColors,
                        scrollBehavior = scrollState,
                        onAvatarClick = {
                            if (accounts.isEmpty()) {
                                onNavigateToLoginPage()
                            } else {
                                showAccountDialog.value = true
                            }
                        }
                    )
                },
                bottomBar = {
                    HomeNavigationBar(
                        selection = currNavPage.selection,
                        containerColor = navigationContainerColor,
                        onNavigate = onHomeNavigate
                    )
                }
            ) { padding ->
                AnimatedContent(
                    targetState = currNavPage,
                    transitionSpec = trans@{
                        val spec = tween<IntOffset>(500)
                        val direction =
                            if (targetState.selection.id > initialState.selection.id) {
                                AnimatedContentScope.SlideDirection.Left
                            } else {
                                AnimatedContentScope.SlideDirection.Right
                            }
                        slideIntoContainer(direction, spec) with slideOutOfContainer(
                            direction,
                            spec
                        )
                    },
                ) { targetState ->
                    targetState.content(padding)
                }
            }
        }

        if (showAccountDialog.value) {
            AccountDialog(
                activeAccount = currAccount,
                accountState = state,
                accounts = accounts.filter { it.id != bot },
                onSwitchAccount = {
                    showAccountDialog.value = false
                    onSwitchAccount(it)
                },
                onLogout = {
                    showAccountDialog.value = false
                    onLogout(it)
                },
                onLogin = {
                    showAccountDialog.value = false
                    onLogin(it)
                },
                onNavigateToLoginPage = {
                    showAccountDialog.value = false
                    onNavigateToLoginPage()
                },
                onDismiss = {
                    showAccountDialog.value = false
                }
            )
        }
    }

}


@Preview
@Composable
fun HomeViewPreview() {
    val list by remember { mutableStateOf(listOf<BasicAccountInfo>()) }
    val navState = remember { mutableStateOf(homeNaves[HomeNavSelection.MESSAGE]!!) }
    ArukuTheme {
        HomeView(
            navState, state = AccountState.Default, accounts = list,
            {}, {}, {}, {}, { _, _ -> },
        )
    }
}