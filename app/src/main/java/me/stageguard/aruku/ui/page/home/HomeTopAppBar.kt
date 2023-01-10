package me.stageguard.aruku.ui.page.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import me.stageguard.aruku.ui.page.home.account.AccountAvatar
import me.stageguard.aruku.ui.theme.ArukuTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(
    botList: List<BasicAccountInfo>,
    title: String,
    state: AccountState,
    modifier: Modifier = Modifier,
    onSwitchAccount: (Long) -> Unit,
    onAddAccount: () -> Unit,
) {
//    val avatarProgressIndicator by remember { derivedStateOf { state.value is AccountState.Login } }
//    val activeAccountOnline by remember { derivedStateOf { state.value is AccountState.Online } }
//    val account = LocalBot.current
//    val context = LocalContext.current
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = TextStyle(fontSize = 35.sp, fontWeight = FontWeight.Bold)
                )

            }
        },
        actions = {
            AccountAvatar(
                accountState = state,
                botList = botList,
                onSwitchAccount = onSwitchAccount,
                onAddAccount = onAddAccount
            )
        }
    )
}


@Preview
@Composable
fun HomeTopAppBarPreview() {
    val expanded = remember { mutableStateOf(true) }
    val state by remember { mutableStateOf(AccountState.Default) }
    ArukuTheme {
        HomeTopAppBar(
            botList = listOf(
                BasicAccountInfo(1234567890, "StageGuard", null),
                BasicAccountInfo(9876543210, "GuardStage", null),
                BasicAccountInfo(1145141919, "WhichWho", null),
            ),
            state = state,
//            showAvatarProgressIndicator = true,
            onAddAccount = { expanded.value = !expanded.value },
            onSwitchAccount = {},
//            activeAccountOnline = true,
            title = "123title"
        )
    }
}