package me.stageguard.aruku.ui.page.home.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.stageguard.aruku.R
import me.stageguard.aruku.ui.LocalAccountState
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.page.home.AccountState
import me.stageguard.aruku.ui.page.home.BasicAccountInfo
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringResC

/**
 * Created by LoliBall on 2022/12/31 20:59.
 * https://github.com/WhichWho
 */
@Composable
fun AccountAvatar(
    accounts: List<BasicAccountInfo>,
    onAddAccount: () -> Unit,
    onSwitchAccount: (Long) -> Unit
) {

    val bot = LocalBot.current
    val accountState = LocalAccountState.current
    val viewModel = viewModel<AccountAvatarViewModel>()

    val showProgress = accountState is AccountState.Login
    val online = accountState is AccountState.Online
    val currentAccount by remember { derivedStateOf { accounts.find { it.id == bot } } }



    AccountMenu(
        accounts,
        viewModel.accountMenuExpanded,
        onClickAccountItem = {
            viewModel.accountMenuExpanded.value = false
            onSwitchAccount(it)
        },
        onDismissAccountMenu = { viewModel.accountMenuExpanded.value = false },
        onClickAddAccount = {
            viewModel.accountMenuExpanded.value = false
            onAddAccount()
        }
    )

}

@Composable
private fun AccountMenu(
    botList: List<BasicAccountInfo>,
    expanded: State<Boolean>,
    onClickAccountItem: (Long) -> Unit,
    onDismissAccountMenu: () -> Unit,
    onClickAddAccount: () -> Unit,
) {
    val botListExpanded by remember { expanded }

    DropdownMenu(
        expanded = botListExpanded,
        onDismissRequest = onDismissAccountMenu
    ) {
        botList.forEach { bot ->
            AccountListItem(
                accountNo = bot.id,
                accountNick = bot.nick,
                avatarImage = bot.avatarUrl,
                onClickAccountItem = onClickAccountItem,
            )
        }
        DropdownMenuItem(
            text = {
                Row(modifier = Modifier.padding(horizontal = 10.dp)) {
                    Icon(
                        Icons.Outlined.AddCircleOutline,
                        contentDescription = "add account",
                        modifier = Modifier
                            .size(40.dp)
                            .padding(2.5.dp)
                            .align(Alignment.CenterVertically)
                            .clip(CircleShape),
                        //placeholder = Icons.Filled.NoAccounts
                    )
                    Text(
                        R.string.add_account.stringResC,
                        modifier = Modifier
                            .padding(start = 15.dp)
                            .align(Alignment.CenterVertically)
                    )
                }
            },
            onClick = onClickAddAccount
        )
    }
}

@Composable
private fun AccountListItem(
    accountNo: Long,
    accountNick: String,
    avatarImage: Any?,
    onClickAccountItem: (Long) -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(modifier = Modifier.padding(horizontal = 10.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarImage)
                        .crossfade(true)
                        .build(),
                    contentDescription = "account avatar",
                    modifier = Modifier
                        .size(40.dp)
                        .padding(2.5.dp)
                        .align(Alignment.CenterVertically)
                        .clip(CircleShape)
                )
                Column(
                    modifier = Modifier
                        .padding(start = 15.dp)
                        .padding(vertical = 10.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Text(
                        accountNick,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                    Text(
                        accountNo.toString(),
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        },
        onClick = { onClickAccountItem(accountNo) }
    )
}

@Preview
@Composable
fun AccountAvatarPreview() {
    ArukuTheme {
        CompositionLocalProvider(LocalAccountState provides AccountState.Default) {
            Row {
                AccountAvatar(remember { listOf() }, {}, {})
                AccountAvatar(remember { listOf() }, {}, {})
                AccountAvatar(remember { listOf() }, {}, {})
            }
        }
    }
}