package me.stageguard.aruku.ui.page.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import me.stageguard.aruku.ui.theme.ArukuTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(
    title: String,
    account: BasicAccountInfo?,
    barColors: TopAppBarColors,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
    onAvatarClick: () -> Unit,
) {
//    val avatarProgressIndicator by remember { derivedStateOf { state.value is AccountState.Login } }
//    val activeAccountOnline by remember { derivedStateOf { state.value is AccountState.Online } }
//    val context = LocalContext.current
    TopAppBar(
        modifier = modifier,
        colors = barColors,
        scrollBehavior = scrollBehavior,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                    style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)
                )

            }
        },
        actions = {
            IconButton(
                onClick = onAvatarClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.padding(end = 10.dp, top = 6.dp)
            ) {
                Box(Modifier.size(45.dp), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(account?.avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "account avatar url",
                        modifier = Modifier
                            .size(45.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun HomeTopAppBarPreview() {
    ArukuTheme {
        HomeTopAppBar(
//            activeAccountOnline = true,
            title = "123title",
            barColors = TopAppBarDefaults.topAppBarColors(),
            account = BasicAccountInfo(1234567890, "StageGuard", null)
        ) {}
    }
}