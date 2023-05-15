package me.stageguard.aruku.ui.page.home.message

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import me.stageguard.aruku.database.ok
import me.stageguard.aruku.service.parcel.ContactId
import me.stageguard.aruku.service.parcel.ContactType
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.common.FastScrollToTopFab
import me.stageguard.aruku.ui.common.WhitePage
import me.stageguard.aruku.ui.page.home.HomeSearchBar
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringResC
import me.stageguard.aruku.util.toFormattedDateTime
import net.mamoe.mirai.utils.Either
import net.mamoe.mirai.utils.Either.Companion.isLeft
import net.mamoe.mirai.utils.Either.Companion.left
import net.mamoe.mirai.utils.Either.Companion.leftOrNull
import net.mamoe.mirai.utils.Either.Companion.right
import net.mamoe.mirai.utils.Either.Companion.rightOrNull
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeMessagePage(
    padding: PaddingValues,
    onContactClick: (contact: ContactId, messageId: Long) -> Unit
) {
    val bot = LocalBot.current
    val viewModel: MessageViewModel = koinViewModel { parametersOf(bot) }

    LaunchedEffect(bot) {
        viewModel.setActiveBot(bot)
    }

    val messages by viewModel.messages.collectAsState()
    val listState = rememberLazyListState()
    val shimmer = rememberShimmer(shimmerBounds = ShimmerBounds.View)
    val currentOnContactClick by rememberUpdatedState(onContactClick)
    val searchBarHeight = 52.dp

    var currentFirstVisibleIndex by remember { mutableStateOf(listState.firstVisibleItemIndex) }
    LaunchedEffect(messages) {
        if (messages is LoadState.Ok) {
            val first = messages.ok().data.firstOrNull()
            if (first != null && listState.firstVisibleItemIndex > currentFirstVisibleIndex) {
                listState.animateScrollToItem((listState.firstVisibleItemIndex - 1).coerceAtLeast(0))
            }
            currentFirstVisibleIndex = listState.firstVisibleItemIndex
        }
    }

    Box {
        if (bot != null && messages !is LoadState.Error) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 32.dp + searchBarHeight,
                    bottom = padding.calculateBottomPadding()
                )
            ) {
                if (messages is LoadState.Ok) {
                    val data = messages.ok().data
                    items(data, key = { it.contact }) {
                        ContactMessageItem(
                            data = Either<Shimmer, SimpleMessagePreview>(it),
                            modifier = Modifier
                                .animateItemPlacement()
                                .animateContentSize(),
                            onClickItem = {
                                viewModel.clearUnreadCount(bot, it.contact)
                                currentOnContactClick(it.contact, it.messageId)
                            }
                        )
                    }
                } else {
                    items(10) {
                        ContactMessageItem(data = Either(shimmer))
                    }
                }
            }
        } else {
            WhitePage(R.string.list_failed.stringResC)
        }

        HomeSearchBar(
            height = searchBarHeight,
            padding = PaddingValues(
                top = padding.calculateTopPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp
            ),
            onSearchValueChanges = { }
        )

        FastScrollToTopFab(listState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactMessageItem(
    data: MessagePreviewOrShimmer,
    modifier: Modifier = Modifier,
    onClickItem: () -> Unit = { },
) {
    val currentOnClickItem by rememberUpdatedState(onClickItem)

    Box(
        modifier = Modifier.fillMaxWidth().height(78.dp).then(modifier).let {
            data.leftOrNull?.run { it.shimmer(this) } ?: it
        }
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { currentOnClickItem() },
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(40.dp),
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
                        .weight(1f)
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
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        ),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }
                Column(
                    modifier = Modifier.padding(end = 12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Top
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
            ContactId(ContactType.GROUP, 123123L),
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
                    ContactMessageItem(
                        Either(shimmer),
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .fillMaxWidth()
                    )
                }
            }
            mockMessages.forEach {
                item {
                    ContactMessageItem(
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