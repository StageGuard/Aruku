package me.stageguard.aruku.ui.page.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.stageguard.aruku.R
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringResC

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(
    botList: List<BasicAccountInfo>,
    botListExpanded: MutableState<Boolean>,
    title: String,
    modifier: Modifier = Modifier,
    onAvatarClick: () -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onAddAccountClick: () -> Unit
) {
    LargeTopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.largeTopAppBarColors(),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = TextStyle(fontSize = 35.sp, fontWeight = FontWeight.Bold)
                )
                IconButton(
                    onClick = onAvatarClick,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        Icons.Outlined.AccountCircle,
                        modifier = Modifier.size(45.dp),
                        contentDescription = "account avatar"
                    )
                }
            }
        },
        actions = {
            DropdownMenu(
                expanded = botListExpanded.value,
                onDismissRequest = { botListExpanded.value = false }
            ) {
                botList.forEach { bot ->
                    AccountListItem(
                        accountNo = bot.id,
                        accountNick = bot.nick,
                        isActive = LocalBot.current?.id == bot.id,
                        avatarImageRequest = if (bot.avatarUrl != null) {
                            ImageRequest.Builder(LocalContext.current)
                                .data(bot.avatarUrl)
                                .crossfade(true)
                                .build()
                        } else null,
                        onSwitchAccount = {
                            botListExpanded.value = !botListExpanded.value
                            onSwitchAccount(bot.id)
                        }
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
                                modifier = Modifier.padding(start = 15.dp).align(Alignment.CenterVertically)
                            )
                        }
                    },
                    onClick = {
                        botListExpanded.value = !botListExpanded.value
                        onAddAccountClick()
                    }
                )
            }
        }
    )
}

@Composable
fun AccountListItem(
    accountNo: Long,
    accountNick: String,
    isActive: Boolean,
    avatarImageRequest: ImageRequest? = null,
    onSwitchAccount: (Long) -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(modifier = Modifier.padding(horizontal = 10.dp)) {
                AsyncImage(
                    avatarImageRequest,
                    contentDescription = "account avatar",
                    modifier = Modifier
                        .size(40.dp)
                        .padding(2.5.dp)
                        .align(Alignment.CenterVertically)
                        .clip(CircleShape),
                    //placeholder = Icons.Filled.NoAccounts
                )
                Column(
                    modifier = Modifier.padding(start = 15.dp).padding(vertical = 10.dp)
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
        onClick = { onSwitchAccount(accountNo) }
    )
}

@Preview
@Composable
fun HomeTopAppBarPreview() {
    val expanded = remember { mutableStateOf(true) }
    ArukuTheme {
        HomeTopAppBar(
            botList = listOf(
                BasicAccountInfo(1234567890, "StageGuard", null),
                BasicAccountInfo(9876543210, "GuardStage", null),
                BasicAccountInfo(1145141919, "WhichWho", null),
            ),
            botListExpanded = expanded,
            onAvatarClick = { expanded.value = !expanded.value },
            onSwitchAccount = {},
            title = "123title"
        ) {}
    }
}