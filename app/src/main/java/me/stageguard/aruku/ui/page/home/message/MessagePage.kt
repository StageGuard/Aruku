package me.stageguard.aruku.ui.page.home.message

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.valentinilk.shimmer.Shimmer
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import me.stageguard.aruku.R
import me.stageguard.aruku.database.LoadState
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.ArukuContactType
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.LocalHomeAccountState
import me.stageguard.aruku.ui.common.FastScrollToTopFab
import me.stageguard.aruku.ui.common.WhitePage
import me.stageguard.aruku.ui.page.home.AccountState
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.cast
import me.stageguard.aruku.util.stringResC
import me.stageguard.aruku.util.toFormattedDateTime
import me.stageguard.aruku.util.toFormattedTime
import net.mamoe.mirai.utils.Either
import net.mamoe.mirai.utils.Either.Companion.ifLeft
import net.mamoe.mirai.utils.Either.Companion.isLeft
import net.mamoe.mirai.utils.Either.Companion.left
import net.mamoe.mirai.utils.Either.Companion.right
import net.mamoe.mirai.utils.Either.Companion.rightOrNull
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeMessagePage(padding: PaddingValues, onContactClick: (ArukuContact, Int) -> Unit) {
    val bot = LocalBot.current
    val accountState = LocalHomeAccountState.current
    val viewModel: MessageViewModel = koinViewModel { parametersOf(bot) }

    LaunchedEffect(accountState) {
        if (accountState is AccountState.Online) viewModel.updateMessages()
    }

    val messages by viewModel.messages.collectAsState()
    val listState = rememberLazyListState()
    val shimmer = rememberShimmer(shimmerBounds = ShimmerBounds.View)
    val currentOnContactClick by rememberUpdatedState(onContactClick)

    val currentFirstVisibleIndex = remember { mutableStateOf(listState.firstVisibleItemIndex) }
    LaunchedEffect(messages) {
        if (messages is LoadState.Ok<List<SimpleMessagePreview>>) {
            val first = messages.cast<LoadState.Ok<List<SimpleMessagePreview>>>().data.firstOrNull()
            if (first != null && listState.firstVisibleItemIndex > currentFirstVisibleIndex.value) {
                listState.animateScrollToItem((listState.firstVisibleItemIndex - 1).coerceAtLeast(0))
            }
            currentFirstVisibleIndex.value = listState.firstVisibleItemIndex
        }
    }

    Box {
        if (bot != null && messages !is LoadState.Error) {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(5.dp)
            ) {
                if (messages is LoadState.Ok) {
                    val data = messages.cast<LoadState.Ok<List<SimpleMessagePreview>>>().data
                    items(data, key = { it.contact }) {
                        MessageCard(
                            data = Either<Shimmer, SimpleMessagePreview>(it),
                            modifier = Modifier
                                .animateItemPlacement()
                                .animateContentSize()
                                .clickable {
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
            WhitePage(R.string.list_failed.stringResC)
        }

        FastScrollToTopFab(listState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageCard(data: MessagePreviewOrShimmer, modifier: Modifier = Modifier) {
    Box(modifier = Modifier
        .fillMaxWidth()
        .apply m@{ data.ifLeft { s -> this@m.shimmer(s) } }
        .then(modifier)
    ) {
        Row(modifier = Modifier.align(Alignment.CenterStart)) {
            Card(
                modifier = Modifier
                    .padding(12.dp)
                    .size(45.dp),
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                AsyncImage(
                    ImageRequest.Builder(LocalContext.current)
                        .data(data.rightOrNull?.avatarData)
                        .crossfade(true)
                        .build(),
                    "message avatar",
                    modifier = Modifier.fillMaxSize().let {
                        if (data.isLeft) {
                            it
                                .shimmer(data.left)
                                .background(
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = CircleShape
                                )
                        } else it
                    },
                    contentScale = ContentScale.Crop
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = data.rightOrNull?.name ?: "",
                    modifier = if (data.isLeft) Modifier
                        .width(120.dp)
                        .height(13.dp)
                        .shimmer(data.left)
                        .background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RectangleShape
                        ) else Modifier,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.requiredSize(1.dp, if (data.isLeft) 10.dp else 5.dp))
                Text(
                    text = data.rightOrNull?.preview ?: "",
                    modifier = if (data.isLeft) Modifier
                        .width(120.dp)
                        .height(11.dp)
                        .shimmer(data.left)
                        .background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RectangleShape
                        ) else Modifier,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.SemiBold
                    ),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .wrapContentSize()
                .padding(end = 14.dp)
        ) {
            Text(
                text = data.rightOrNull?.time?.toFormattedDateTime() ?: "",
                modifier = Modifier.let {
                    if (data.isLeft) {
                        it
                            .size(35.dp, 12.dp)
                            .shimmer(data.left)
                            .background(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = RectangleShape
                            )
                    } else it
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.requiredSize(1.dp, if (data.isLeft) 7.dp else 5.dp))
            Badge(
                modifier = Modifier
                    .align(Alignment.End)
                    .offset(y = 1.dp)
                    .let {
                        if (data.isLeft) {
                            it
                                .shimmer(data.left)
                                .background(
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = CircleShape.copy(CornerSize(100))
                                )
                        } else {
                            it
                                .offset(x = (-1).dp)
                                .alpha(if (data.right.unreadCount == 0) 0f else 1f)
                        }
                    }
            ) {
                Text(
                    text = data.rightOrNull?.unreadCount?.toString() ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview
@Composable
fun MessageCardPreview() {
    val zf = ZoneOffset.ofHours(+8)
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
            LocalDateTime.now().minusHours((0..36).random().toLong()).toEpochSecond(zf),
            (0..2).random(),
            0
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
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .fillMaxWidth()
                    )
                }
            }
            mockMessages.forEach {
                item {
                    MessageCard(
                        Either<Shimmer, SimpleMessagePreview>(it),
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}