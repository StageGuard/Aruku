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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFrom
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.MainAxisAlignment
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.theme.ArukuTheme

@Composable
fun ChatListView(
    chatList: List<ChatElement>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val bot = LocalBot.current
    val context = LocalContext.current

    Box(modifier = modifier) {
        LazyColumn(
            state = lazyListState,
            contentPadding = paddingValues,
            modifier = Modifier.fillMaxWidth()
        ) {
            chatList.forEachIndexed { index, element ->
                when (element) {
                    is ChatElement.Message -> item(key = element.source) {
                        val lastSentByCurrent = chatList.getOrNull(index - 1).run {
                            this is ChatElement.Message && this.senderId == element.senderId
                        }
                        Mesasge(
                            context = context,
                            source = element.source,
                            senderId = element.senderId,
                            senderName = element.senderName,
                            senderAvatarData = element.senderAvatarUrl,
                            sentByBot = element.senderId == bot,
                            showSender = !lastSentByCurrent,
                            time = element.time,
                            visibleMessages = element.visibleMessages,
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = if (lastSentByCurrent) 2.dp else 5.dp
                            )
                        ) { }
                    }

                    is ChatElement.DateDivider -> item(key = "date_divider") { DateDivider(element.date) }
                    is ChatElement.Notification -> item(key = "notification") {
                        Notification(element.content, element.annotated)
                    }
                }
            }
        }
    }
}

@Composable
private fun Mesasge(
    context: Context,
    source: Int,
    senderId: Long,
    senderName: String,
    senderAvatarData: Any?,
    sentByBot: Boolean,
    showSender: Boolean,
    time: String,
    visibleMessages: List<VisibleChatMessage>,
    modifier: Modifier = Modifier,
    onClickAvatar: (Long) -> Unit,
) {
    @Composable
    fun RowScope.Avatar(modifier: Modifier = Modifier) {
        if (!sentByBot) {
            val layoutModifier = Modifier
                .padding(end = 10.dp)
                .size(42.dp)
                .align(Alignment.Top)
            if (showSender) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(senderAvatarData).crossfade(true)
                        .build(),
                    contentDescription = "avatar of $senderId",
                    modifier = layoutModifier
                        .clickable { onClickAvatar(senderId) }
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clip(CircleShape)
                        .then(modifier),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Spacer(modifier = layoutModifier.then(modifier))
            }
        }
    }

    @Composable
    fun RowScope.MessageContent(modifier: Modifier = Modifier) {
        Column(modifier = modifier) {
            if (!sentByBot && showSender) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier
                        .align(Alignment.Start)
                        .paddingFrom(LastBaseline, after = 8.dp)
                        .padding(top = 3.dp, bottom = 3.dp)
                )
            }
            Surface(
                color = if (sentByBot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(
                    if (sentByBot) 20.dp else 4.dp,
                    if (sentByBot) 4.dp else 20.dp,
                    20.dp,
                    20.dp
                )

            ) {
                RichMessage(
                    list = visibleMessages,
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .align(if (!sentByBot) Alignment.Start else Alignment.End)
                .then(modifier)
        ) {
            if (!sentByBot) Avatar(modifier = Modifier.align(Alignment.Top))
            MessageContent(
                modifier = Modifier
                    .padding(start = if (sentByBot) 52.dp else 0.dp)
                    .padding(end = if (sentByBot) 0.dp else 20.dp)
                    .wrapContentSize()
            )
        }
    }
}

@Composable
private fun RichMessage(
    list: List<VisibleChatMessage>,
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
        val single = list.singleOrNull() ?: return@run 10.dp
        if (
            single is VisibleChatMessage.Image ||
            single is VisibleChatMessage.FlashImage ||
            single is VisibleChatMessage.Audio ||
            single is VisibleChatMessage.File ||
            single is VisibleChatMessage.Forward
        ) return@run 0.dp else return@run 10.dp
    }

    if (list.size == 1 && list.singleOrNull().run {
            this is VisibleChatMessage.Image || this is VisibleChatMessage.FlashImage
        }) {
        Box(
            modifier = modifier.then(Modifier.padding(2.dp))
        ) {
            when (val image = list.single()) {
                is VisibleChatMessage.Image -> {
                    Image(element = image, context = context, onClick = {})
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
                    .padding(end = 10.dp, bottom = 8.dp)
            )
        }
    } else {
        Column {
            FlowRow(
                mainAxisAlignment = if (list.size == 1) MainAxisAlignment.Center else MainAxisAlignment.Start,
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
                        is VisibleChatMessage.Audio -> Audio(msg) { }
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

@Preview
@Composable
fun ChatListPreview() {
    ArukuTheme {
        CompositionLocalProvider(LocalBot provides 202746796L) {
            val listState = rememberLazyListState()
            val randSrcId = { IntRange(0, 100000).random() }
            val list = listOf<ChatElement>(
                ChatElement.DateDivider("Jan 4, 2023"),
                ChatElement.Notification("XXX toggled mute all", listOf()),
                ChatElement.Message(
                    senderId = 1355416608L,
                    senderName = "StageGuard",
                    senderAvatarUrl = "https://stageguard.top/img/avatar.png",
                    time = "11:45:14",
                    source = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.PlainText("compose chat list view preview")
                    ),
                ),
                ChatElement.Message(
                    senderId = 1355416608L,
                    senderName = "StageGuard",
                    senderAvatarUrl = "https://stageguard.top/img/avatar.png",
                    time = "11:45:14",
                    source = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.PlainText(buildString { repeat(20) { append("long message! ") } })
                    ),
                ),
                ChatElement.Message(
                    senderId = 3129693328L,
                    senderName = "WhichWho",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=3129693328&s=0&timestamp=1673582758562",
                    time = "11:45:14",
                    source = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.Face(1),
                        VisibleChatMessage.Face(10),
                        VisibleChatMessage.PlainText("<- this is face.")
                    ),
                ),
                ChatElement.Message(
                    senderId = 202746796L,
                    senderName = "SIGTERM",
                    senderAvatarUrl = "",
                    time = "11:45:14",
                    source = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.Image("https://gchat.qpic.cn/gchatpic_new/2591482572/2079312506-2210827314-3599E59C0E36C66A966F4DD2E28C4341/0?term=255&is_origin=0")
                    ),
                ),
                ChatElement.Message(
                    senderId = 202746796L,
                    senderName = "SIGTERM",
                    senderAvatarUrl = "",
                    time = "11:45:14",
                    source = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.At(3129693328L, "WhichWho"),
                        VisibleChatMessage.PlainText(" this is my error log"),
                        VisibleChatMessage.Image("https://mirai.mamoe.net/assets/uploads/files/1672243675745-ece9effe-c9eb-4bcb-aba1-529e6f0c5f49-image.png")

                    ),
                ),
            )

            ChatListView(list, listState, PaddingValues())
        }
    }
}