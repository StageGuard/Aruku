package me.stageguard.aruku.ui.page.chat

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFrom
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemsIndexed
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.MainAxisAlignment
import me.stageguard.aruku.ui.LocalBot

@Composable
fun ChatListView(
    chatList: LazyPagingItems<ChatElement>,
    audioStatus: Map<String, ChatAudioStatus>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
    onRegisterAudioStatusListener: (fileMd5: String) -> Unit,
    onUnRegisterAudioStatusListener: (fileMd5: String) -> Unit,
) {
    val bot = LocalBot.current
    val context = LocalContext.current

    LaunchedEffect(true) {
        lazyListState.scrollToItem(0)
    }

    LaunchedEffect(key1 = chatList) {
        println(chatList.itemCount)
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = lazyListState,
            contentPadding = paddingValues,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(chatList, { _, element -> element.uniqueKey }) { index, element ->
                when (element) {
                    is ChatElement.Message -> {
                        val nextSentByCurrent =
                            if (index + 1 >= chatList.itemCount) false else chatList[index + 1].run {
                                this is ChatElement.Message && this.senderId == element.senderId
                            }

                        // observe audio status in composable
                        val audio = element.visibleMessages.filterIsInstance<VisibleChatMessage.Audio>().firstOrNull()
                        audio?.disposableObserver(
                            onRegister = onRegisterAudioStatusListener,
                            onUnregister = onUnRegisterAudioStatusListener
                        )

                        Message(
                            context = context,
                            messageId = element.messageId,
                            senderId = element.senderId,
                            senderName = element.senderName,
                            senderAvatarData = element.senderAvatarUrl,
                            sentByBot = element.senderId == bot,
                            showSender = !nextSentByCurrent,
                            time = element.time,
                            messages = element.visibleMessages,
                            audioStatus = audio?.run { audioStatus[identity] },
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = if (nextSentByCurrent) 2.dp else 5.dp
                            ),
                            onClickAvatar = { }
                        )
                    }

                    is ChatElement.DateDivider -> {
                        DateDivider(element.date)
                    }

                    is ChatElement.Notification -> {
                        Notification(element.content, element.annotated)
                    }

                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun Message(
    context: Context,
    messageId: Int,
    senderId: Long,
    senderName: String,
    senderAvatarData: Any?,
    sentByBot: Boolean,
    showSender: Boolean,
    time: String,
    messages: List<VisibleChatMessage>,
    audioStatus: ChatAudioStatus?,
    modifier: Modifier = Modifier,
    onClickAvatar: (Long) -> Unit,
) {

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .align(if (!sentByBot) Alignment.Start else Alignment.End)
                .then(modifier)
        ) {
            if (!sentByBot) { // avatar
                val avatarModifier = Modifier
                    .padding(end = 10.dp)
                    .size(42.dp)
                    .align(Alignment.Top)
                if (showSender) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(senderAvatarData).crossfade(true)
                            .build(),
                        contentDescription = "avatar of $senderId",
                        modifier = avatarModifier
                            .clickable { onClickAvatar(senderId) }
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clip(CircleShape)
                            .then(Modifier.align(Alignment.Top)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Spacer(modifier = Modifier.align(Alignment.Top))
                }
            }

            // message content
            Column(
                modifier = Modifier
                    .padding(
                        start = if (!sentByBot && !showSender) 52.dp else (if (sentByBot) 26.dp else 0.dp),
                        end = if (sentByBot) 0.dp else 26.dp
                    )
                    .wrapContentSize()
            ) {
                if (!sentByBot && showSender) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier
                            .align(Alignment.Start)
                            .paddingFrom(LastBaseline, after = 8.dp)
                            .padding(top = 3.dp, bottom = 3.dp)
                    )
                }
                Surface(
                    color = if (sentByBot) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(
                        if (sentByBot) 20.dp else 4.dp,
                        if (sentByBot) 4.dp else 20.dp,
                        20.dp,
                        20.dp
                    )

                ) {
                    RichMessage(
                        list = messages,
                        audioStatus = audioStatus,
                        time = time,
                        context = context,
                        sentByBot = sentByBot,
                        modifier = Modifier
                            .wrapContentSize()
                            .defaultMinSize(40.dp)
                    ) {

                    }
                }
            }
        }
    }
}

@Composable
private fun RichMessage(
    list: List<VisibleChatMessage>,
    audioStatus: ChatAudioStatus?,
    time: String,
    context: Context,
    sentByBot: Boolean,
    modifier: Modifier = Modifier,
    onClickAnnotated: (VisibleChatMessage) -> Unit,
) {
    @Composable
    fun MessageTimeIndicator(color: Color, textPadding: Dp = 0.dp, mdf: Modifier = Modifier) {
        Surface(
            color = color,
            shape = RoundedCornerShape(5.dp),
            modifier = mdf
        ) {
            Text(
                text = time,
                modifier = Modifier.padding(textPadding),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (sentByBot) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }

    val contentPadding = run {
        val single = list.singleOrNull() ?: return@run 8.dp
        if (
            single is VisibleChatMessage.Image ||
            single is VisibleChatMessage.FlashImage ||
            single is VisibleChatMessage.Audio ||
            single is VisibleChatMessage.File ||
            single is VisibleChatMessage.Forward
        ) return@run 0.dp else return@run 8.dp
    }

    if (list.size == 1 && list.singleOrNull().run {
            this is VisibleChatMessage.Image || this is VisibleChatMessage.FlashImage
        }) { // only a image
        Box(
            modifier = modifier.then(Modifier.padding(2.dp))
        ) {
            when (val image = list.single()) {
                is VisibleChatMessage.Image -> {
                    Image(
                        element = image,
                        context = context,
                        modifier = Modifier.defaultMinSize(minHeight = 54.dp),
                        onClick = {}
                    )
                }

                is VisibleChatMessage.FlashImage -> {
                    FlashImage(element = image, context = context, onClick = {})
                }

                else -> error("UNREACHABLE")
            }
            MessageTimeIndicator(
                color = Color.Black.copy(alpha = 0.3f),
                textPadding = 2.dp,
                mdf = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    } else {
        Column {
            FlowRow(
                mainAxisAlignment = if (list.size == 1) {
                    MainAxisAlignment.Center
                } else {
                    MainAxisAlignment.Start
                },
                modifier = modifier.then(
                    Modifier
                        .padding(2.dp)
                        .padding(horizontal = contentPadding)
                        .padding(top = contentPadding, bottom = 3.dp)
                )
            ) {
                list.forEach { msg ->
                    when (msg) {
                        is VisibleChatMessage.PlainText -> PlainText(msg, sentByBot)
                        is VisibleChatMessage.Image -> Image(msg, context) { }
                        is VisibleChatMessage.At -> At(msg, sentByBot) { }
                        is VisibleChatMessage.AtAll -> AtAll(msg, sentByBot)
                        is VisibleChatMessage.Face -> Face(msg, context)
                        is VisibleChatMessage.FlashImage -> FlashImage(msg, context) { }
                        is VisibleChatMessage.Audio -> {
                            Audio(msg, audioStatus) { }
                        }

                        is VisibleChatMessage.File -> File(msg) { }
                        is VisibleChatMessage.Forward -> {}
                        is VisibleChatMessage.Unsupported -> Unsupported(msg)

                    }
                }
            }
            MessageTimeIndicator(
                color = Color.Transparent,
                mdf = Modifier
                    .align(Alignment.End)
                    .padding(end = 10.dp, bottom = 8.dp)
                    .padding(start = 30.dp)
            )
        }
    }
}

@Composable
private fun Notification(
    content: String,
    annotated: List<Pair<IntRange, () -> Unit>>,
    modifier: Modifier = Modifier
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            shape = RoundedCornerShape(7.dp),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .then(modifier),
        ) {
            Text(
                text = content,
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
            )
        }
    }
}

@Composable
private fun DateDivider(dayString: String, modifier: Modifier = Modifier) {
    @Composable
    fun RowScope.DateDividerLineLine() {
        Divider(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    }

    Row(
        modifier = modifier.then(
            Modifier
                .padding(vertical = 8.dp, horizontal = 16.dp)
                .height(16.dp)
        )
    ) {
        DateDividerLineLine()
        Text(
            text = dayString,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DateDividerLineLine()
    }
}

/*
@Preview
@Composable
fun ChatListPreview() {
    ArukuTheme {
        CompositionLocalProvider(LocalBot provides 202746796L) {
            val listState = rememberLazyListState()
            val randSrcId = { IntRange(0, 100000).random() }

            ChatListView(
                flow { emit(PagingData.from(list.asReversed())) }.collectAsLazyPagingItems(),
                listState,
                PaddingValues()
            )
        }
    }
}*/
