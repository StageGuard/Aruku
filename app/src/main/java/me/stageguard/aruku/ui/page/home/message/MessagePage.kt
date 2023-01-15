package me.stageguard.aruku.ui.page.home.message

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.items
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.valentinilk.shimmer.Shimmer
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import me.stageguard.aruku.R
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.ArukuContactType
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.LocalHomeAccountState
import me.stageguard.aruku.ui.common.FastScrollToTopFab
import me.stageguard.aruku.ui.common.WhitePage
import me.stageguard.aruku.ui.page.home.AccountState
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.formatHHmm
import me.stageguard.aruku.util.stringResC
import net.mamoe.mirai.utils.Either
import net.mamoe.mirai.utils.Either.Companion.ifLeft
import net.mamoe.mirai.utils.Either.Companion.onLeft
import net.mamoe.mirai.utils.Either.Companion.onRight
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeMessagePage(padding: PaddingValues, onContactClick: (ArukuContact, Long) -> Unit) {
    val bot = LocalBot.current
    val accountState = LocalHomeAccountState.current
    val viewModel: MessageViewModel = koinViewModel()

    LaunchedEffect(bot) {
        if (bot != null) viewModel.initMessage(bot)
    }
    LaunchedEffect(accountState) {
        if (accountState is AccountState.Online) viewModel.updateMessages()
    }

    val messages = viewModel.messages.value.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val shimmer = rememberShimmer(shimmerBounds = ShimmerBounds.View)
    val currentOnContactClick by rememberUpdatedState(onContactClick)

    val currentFirstVisibleIndex = remember { mutableStateOf(listState.firstVisibleItemIndex) }
    LaunchedEffect(messages.itemSnapshotList) {
        val first = messages.itemSnapshotList.firstOrNull()
        if (first != null && listState.firstVisibleItemIndex > currentFirstVisibleIndex.value) {
            listState.animateScrollToItem((listState.firstVisibleItemIndex - 1).coerceAtLeast(0))
        }
        currentFirstVisibleIndex.value = listState.firstVisibleItemIndex
    }

    Box {
        if (bot != null && messages.loadState.refresh !is LoadState.Error) {
            if (messages.loadState.refresh !is LoadState.NotLoading || messages.itemCount > 0) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(padding),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(5.dp)
                ) {
                    if (messages.itemCount > 0) {
                        items(messages, key = { it.contact }) {
                            if (it != null) MessageCard(
                                data = Either<Shimmer, SimpleMessagePreview>(it),
                                modifier = Modifier.animateItemPlacement().animateContentSize().clickable {
                                    viewModel.clearUnreadCount(bot, it.contact)
                                    currentOnContactClick(it.contact, it.messageId)
                                }
                            )
                        }
                    } else {
                        items(10) {
                            MessageCard(data = Either(shimmer))
                        }
                    }
                }
            } else {
                WhitePage(R.string.list_empty.stringResC)
            }
        } else {
            WhitePage(R.string.list_failed.stringResC)
        }

        FastScrollToTopFab(listState)
    }
}

@Composable
fun MessageCard(data: MessagePreviewOrShimmer, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier, elevation = CardDefaults.elevatedCardElevation(4.dp)) {
        Box(modifier = Modifier.fillMaxWidth().apply m@{ data.ifLeft { s -> this@m.shimmer(s) } }) {
            Row(modifier = Modifier.align(Alignment.CenterStart)) {
                Card(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(45.dp),
                    shape = CircleShape,
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    data.onRight { m ->
                        AsyncImage(
                            ImageRequest.Builder(LocalContext.current)
                                .data(m.avatarData)
                                .crossfade(true)
                                .build(),
                            "message avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }.onLeft { s ->
                        Box(
                            modifier = Modifier.fillMaxSize().shimmer(s).background(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = CircleShape
                            )
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    data.onRight { m ->
                        Text(
                            text = m.name,
                            modifier = Modifier,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }.onLeft { s ->
                        Box(
                            modifier = Modifier.size(120.dp, 16.dp).shimmer(s).background(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = RectangleShape
                            )
                        )
                        Spacer(modifier = modifier.size(100.dp, 5.dp))
                    }
                    data.onRight { m ->
                        Text(
                            text = m.preview,
                            modifier = Modifier,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.SemiBold
                            ),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }.onLeft { s ->
                        Box(
                            modifier = Modifier.size(120.dp, 14.dp).shimmer(s)
                                .shimmer(s).background(
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = RectangleShape
                                )
                        )
                    }
                }
            }
            data.onRight { m ->
                Text(
                    text = m.time.formatHHmm(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                )
            }.onLeft { s ->
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).size(35.dp, 12.dp)
                        .shimmer(s).background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RectangleShape
                        )
                )
            }
            data.onRight { m ->
                Text(
                    text = m.unreadCount.toString(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = CircleShape.copy(CornerSize(100))
                        )
                        .padding(3.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
            }.onLeft { s ->
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(14.dp).size(20.dp, 20.dp).shimmer(s).background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = CircleShape.copy(CornerSize(100))
                        )
                )
            }
        }
    }
}

@Preview
@Composable
fun MessageCardPreview() {
    val mockMessages = buildList {
        for (i in 1..10) {
            this += R.mipmap.ic_launcher to "MockUserName$i"
        }
    }.shuffled().map { (icon, message) ->
        SimpleMessagePreview(
            ArukuContact(ArukuContactType.GROUP, 123123L),
            icon,
            message,
            "message preview",
            LocalDateTime.now().minusMinutes((0L..3600L).random()),
            (0..100).random(),
            0L
        )
    }
    val shimmer = rememberShimmer(ShimmerBounds.View)

    ArukuTheme {
        LazyColumn(modifier = Modifier.width(300.dp)) {
            repeat(3) {
                item {
                    MessageCard(
                        Either(shimmer),
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .fillMaxWidth()
                    )
                }
            }
            mockMessages.forEach {
                item {
                    MessageCard(
                        Either<Shimmer, SimpleMessagePreview>(it),
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}