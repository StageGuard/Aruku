package me.stageguard.aruku.ui.page.home.account

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.stageguard.aruku.R
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.page.home.AccountState
import me.stageguard.aruku.ui.page.home.BasicAccountInfo
import me.stageguard.aruku.ui.theme.ColorAccountOffline
import me.stageguard.aruku.ui.theme.ColorAccountOnline
import me.stageguard.aruku.util.stringResC

/**
 * Created by LoliBall on 2022/12/31 20:59.
 * https://github.com/WhichWho
 */
@Composable
fun AccountAvatar(
    accountState: State<AccountState>,
    botList: List<BasicAccountInfo>
) {

    val showProgress by remember { derivedStateOf { accountState.value is AccountState.Login } }
    val online by remember { derivedStateOf { accountState.value is AccountState.Online } }
    val bot = LocalBot.current

    IconButton(onClick = { /*onAvatarClick*/ }) {
        Box(Modifier.size(45.dp), contentAlignment = Alignment.Center) {
            if (showProgress) CircularProgressIndicator(modifier = Modifier.fillMaxWidth())
            val currentAccount by remember { derivedStateOf { botList.find { it.id == bot } } }
            if (currentAccount == null) {
                Icon(
                    Icons.Outlined.AccountCircle,
                    contentDescription = "account avatar url",
                    modifier = Modifier
                        .size(40.dp)
                        .border(
                            width = ProgressIndicatorDefaults.CircularStrokeWidth,
                            shape = CircleShape,
                            color = if (showProgress) Color.Transparent else {
                                if (online) ColorAccountOnline else ColorAccountOffline
                            }
                        )
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentAccount!!.avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "account avatar url",
                    modifier = Modifier
                        .size(40.dp)
                        .border(
                            width = ProgressIndicatorDefaults.CircularStrokeWidth,
                            shape = CircleShape,
                            color = if (showProgress) Color.Transparent
                            else if (online) ColorAccountOnline
                            else ColorAccountOffline
                        )
                )
            }
        }
    }

    AccountMenu(botList)

}

@Composable
private fun AccountMenu(botList: List<BasicAccountInfo>) {

    val vm = viewModel<AccountAvatarViewModel>()
    var botListExpanded by remember { vm.dropDownExpend }

    DropdownMenu(
        expanded = botListExpanded,
        onDismissRequest = { botListExpanded = false }
    ) {
        botList.forEach { bot ->
            AccountListItem(
                accountNo = bot.id,
                accountNick = bot.nick,
                avatarImage = bot.avatarUrl
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
            onClick = { vm.onAddAccount() }
        )
    }
}

@Composable
private fun AccountListItem(accountNo: Long, accountNick: String, avatarImage: Any?) {

    val vm = viewModel<AccountAvatarViewModel>()

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
        onClick = { vm.onSwitchAccount(accountNo) }
    )
}