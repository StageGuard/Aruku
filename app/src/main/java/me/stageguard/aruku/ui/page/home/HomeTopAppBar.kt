package me.stageguard.aruku.ui.page.home

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.stageguard.aruku.R
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.page.home.account.AccountAvatar
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.ui.theme.ColorAccountOffline
import me.stageguard.aruku.ui.theme.ColorAccountOnline
import me.stageguard.aruku.util.stringResC

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(
    botList: List<BasicAccountInfo>,
    botListExpanded: MutableState<Boolean>,
    title: String,
    state: State<AccountState>,
    modifier: Modifier = Modifier,
    onAvatarClick: () -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onAddAccountClick: () -> Unit,
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
            AccountAvatar(accountState = state, botList = botList)
        }
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
            state = mutableStateOf(AccountState.Default),
//            showAvatarProgressIndicator = true,
            onAvatarClick = { expanded.value = !expanded.value },
            onSwitchAccount = {},
//            activeAccountOnline = true,
            title = "123title"
        ) {}
    }
}