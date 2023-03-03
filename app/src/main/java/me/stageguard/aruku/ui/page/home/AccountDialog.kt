package me.stageguard.aruku.ui.page.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.stageguard.aruku.R
import me.stageguard.aruku.ui.common.ExpandedAnimatedVisibility
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.ui.theme.ColorAccountOffline
import me.stageguard.aruku.ui.theme.ColorAccountOnline
import me.stageguard.aruku.util.stringResC

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDialog(
    activeAccount: BasicAccountInfo?,
    accountState: AccountState? = AccountState.Default,
    accounts: List<BasicAccountInfo> = listOf(),
    onSwitchAccount: (Long) -> Unit,
    onLogin: (Long) -> Unit,
    onLogout: (Long) -> Unit,
    onNavigateToLoginPage: () -> Unit,
    onDismiss: () -> Unit = { },
) {
    val expanded = remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        )
    ) {
        Surface(
            modifier = Modifier
                .widthIn(0.dp, 575.dp)
                .padding(horizontal = 16.dp)
                .wrapContentHeight()
                .animateContentSize(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .wrapContentSize()
                            .padding(4.dp)
                            .padding(end = 10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = CircleShape,
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(activeAccount?.avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "account avatar url",
                            modifier = Modifier
                                .size(45.dp)
                                .clip(CircleShape)
                        )
                    }
                    Column(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = activeAccount?.nick ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = activeAccount?.id?.toString() ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .padding(top = 20.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Badge(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(12.dp),
                        containerColor = if (accountState is AccountState.Online)
                            ColorAccountOnline else ColorAccountOffline,
                    )
                    Text(
                        text = (if (accountState is AccountState.Online)
                            R.string.home_account_dialog_online else
                            R.string.home_account_dialog_offline).stringResC,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Normal,
                        )
                    )
                }
                Row(modifier = Modifier.padding(start = 6.dp)) {
                    AnimatedVisibility(
                        visible = activeAccount != null,
                        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                    ) {
                        OutlinedButton(
                            onClick = {
                                if(accountState is AccountState.Online) onLogout(activeAccount!!.id)
                                    else onLogin(activeAccount!!.id)
                          },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp)

                        ) {
                            Text(
                                text = (if(accountState is AccountState.Online) R.string.home_account_dialog_logout
                                        else R.string.home_account_dialog_login).stringResC,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    Spacer(
                        modifier = Modifier.width(
                            animateDpAsState(if (activeAccount != null) 12.dp else 0.dp).value
                        )
                    )
                    OutlinedButton(
                        onClick = onNavigateToLoginPage,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp)

                    ) {
                        Text(
                            text = R.string.home_account_dialog_new.stringResC,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(
                        modifier = Modifier.width(
                            animateDpAsState(if (accounts.isNotEmpty()) 12.dp else 0.dp).value
                        )
                    )
                    AnimatedVisibility(
                        visible = accounts.isNotEmpty(),
                        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                    ) {
                        OutlinedButton(
                            onClick = { expanded.value = !expanded.value },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp)

                        ) {
                            Text(
                                text = if (expanded.value) R.string.home_account_dialog_hide_all.stringResC
                                else R.string.home_account_dialog_show_all.stringResC,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
                ExpandedAnimatedVisibility(expanded = expanded.value) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .padding(top = 10.dp)
                            .padding(horizontal = 10.dp)
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceColorAtElevation(0.dp),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(all = 12.dp)
                            .animateContentSize()
                    ) {
                        for (account in accounts) {
                            Row(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSwitchAccount(account.id) }
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(account.avatarUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "other account list account avatar url",
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = buildAnnotatedString {
                                        append(AnnotatedString(text = account.nick))
                                        append(
                                            AnnotatedString(
                                                text = "(${account.id})",
                                                spanStyle = SpanStyle(fontWeight = FontWeight.SemiBold)
                                            )
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun AccountDialogPreview() {
    ArukuTheme {
        AccountDialog(
            activeAccount = BasicAccountInfo(
                id = 1355416608L,
                nick = "StageGuard",
                avatarUrl = "https://stageguard.top/img/avatar.png"
            ),
            accounts = listOf(
                BasicAccountInfo(
                    id = 3129693328L,
                    nick = "WhichWho",
                    avatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=3129693328&s=0&timestamp=1673582758562",
                ),
                BasicAccountInfo(
                    id = 202746796L,
                    nick = "SIGTERM",
                    avatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=202746796&s=0&timestamp=1677720102180",
                )
            ),
            onSwitchAccount = {},
            onLogout = {},
            onLogin = {},
            onNavigateToLoginPage = {},
            onDismiss = {}
        )
    }
}