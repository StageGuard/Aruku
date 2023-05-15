package me.stageguard.aruku.ui.page.chat

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemsIndexed
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.MainAxisAlignment
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.LocalPrimaryMessage
import kotlin.math.min

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
                        val lastSentByCurrent =
                            if (index + 1 >= chatList.itemCount) false else chatList[index + 1].run {
                                this is ChatElement.Message && this.senderId == element.senderId
                            }

                        val nextSentByCurrent =
                            if (index == 0) false else chatList[index - 1].run {
                                this is ChatElement.Message && this.senderId == element.senderId
                            }

                        // observe audio status in composable
                        val audio = element.visibleMessages.filterIsInstance<VisibleChatMessage.Audio>().firstOrNull()
                        audio?.disposableObserver(
                            onRegister = onRegisterAudioStatusListener,
                            onUnregister = onUnRegisterAudioStatusListener
                        )

                        val sentByBot = element.senderId == bot
                        CompositionLocalProvider(LocalPrimaryMessage provides sentByBot) {
                            Message(
                                context = context,
                                messageId = element.messageId,
                                senderId = element.senderId,
                                senderName = element.senderName,
                                senderAvatar = element.senderAvatarUrl,
                                isAlignmentStart = !sentByBot,
                                showAvatar = !lastSentByCurrent && !sentByBot,
                                occupyAvatarSpace = !sentByBot,
                                showSender = !lastSentByCurrent && !sentByBot,
                                topCorner = !lastSentByCurrent,
                                bottomCorner = !nextSentByCurrent,
                                startCorner = sentByBot,
                                endCorner = !sentByBot,
                                time = element.time,
                                messages = element.visibleMessages,
                                audioStatus = audio?.run { audioStatus[identity] },
                                modifier = Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = if (nextSentByCurrent || lastSentByCurrent) 2.dp else 5.dp
                                ),
                                onClickAvatar = { }
                            )
                        }
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
    messageId: Long,
    senderId: Long,
    senderName: String,
    senderAvatar: Any?,
    isAlignmentStart: Boolean, // align layout to left (avatar|message) if true
    showAvatar: Boolean, // show avatar if true
    occupyAvatarSpace: Boolean, // set a empty space as place holder of avatar if !showAvatar
    showSender: Boolean,
    topCorner: Boolean,
    bottomCorner: Boolean,
    startCorner: Boolean, // start corner if true or else end corner of message content
    endCorner: Boolean,
    time: String,
    messages: List<VisibleChatMessage>,
    audioStatus: ChatAudioStatus?,
    modifier: Modifier = Modifier,
    onClickAvatar: (Long) -> Unit,
) {
    val density = LocalDensity.current
    val isPrimary = LocalPrimaryMessage.current

    val avatarSize = 45.dp
    val avatarMargin = 6.dp
    val messageContentSideMargin = 45.dp
    val roundCornerSize = 16.dp

    val avatarWidth = remember(density, showAvatar) {
        if (showAvatar || occupyAvatarSpace) with(density) { (avatarSize + avatarMargin).roundToPx() } else 0
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val constraintSet = ConstraintSet {
            val avatarRef = createRefFor("avatar")
            val senderNameRef = createRefFor("senderName")
            //val senderIdentityRef = createRefFor("senderIdentity")
            val messageContentRef = createRefFor("messageContent")

            val calculatedMargin = if (showAvatar) avatarMargin else
                if (occupyAvatarSpace) (avatarSize + avatarMargin) else 0.dp

            constrain(avatarRef) {
                top.linkTo(parent.top)
                if (isAlignmentStart) start.linkTo(parent.start)
                if (!isAlignmentStart) end.linkTo(parent.end)
            }
            constrain(senderNameRef) {
                top.linkTo(parent.top, 2.dp)
                if (isAlignmentStart) start.linkTo(
                    anchor = if (showAvatar) avatarRef.end else parent.start,
                    margin = calculatedMargin
                )
                if (!isAlignmentStart) end.linkTo(
                    if (showAvatar) avatarRef.start else parent.end,
                    margin = calculatedMargin
                )
            }
            constrain(messageContentRef) {
                top.linkTo(
                    if(showSender) senderNameRef.bottom else parent.top,
                    if(showSender) 5.dp else 0.dp
                )
                if (isAlignmentStart) start.linkTo(
                    anchor = if (showAvatar) avatarRef.end else parent.start,
                    margin = calculatedMargin
                )
                if (!isAlignmentStart) end.linkTo(
                    if (showAvatar) avatarRef.start else parent.end,
                    margin = calculatedMargin
                )
            }
        }

        ConstraintLayout(
            constraintSet = constraintSet,
            modifier = modifier
                .wrapContentHeight()
                .fillMaxWidth()
        ) {
            val messageContentWidth = remember(density, constraints.maxWidth, showAvatar) {
                constraints.maxWidth - avatarWidth - with(density) { messageContentSideMargin.roundToPx() }
            }

            if(showAvatar) AsyncImage(
                model = ImageRequest.Builder(context).data(senderAvatar).crossfade(true)
                    .build(),
                contentDescription = "avatar of $senderId",
                modifier = Modifier
                    .layoutId("avatar")
                    .size(avatarSize)
                    .clip(CircleShape)
                    .clickable { onClickAvatar(senderId) },
                contentScale = ContentScale.Crop,
            )
            if (showSender) Text(
                text = senderName,
                style = MaterialTheme.typography.bodyMedium
                    .copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.layoutId("senderName")
            )
            Surface(
                color = MaterialTheme.colorScheme.run { if (isPrimary) primary else surfaceVariant },
                shape = RoundedCornerShape(
                    if (startCorner) roundCornerSize else if (topCorner) roundCornerSize else 4.dp,
                    if (endCorner) roundCornerSize else if (topCorner) roundCornerSize else 4.dp,
                    if (endCorner) roundCornerSize else if (bottomCorner) roundCornerSize else 4.dp,
                    if (startCorner) roundCornerSize else if (bottomCorner) roundCornerSize else 4.dp,
                ),
                modifier = Modifier
                    .layoutId("messageContent")
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(min(messageContentWidth, placeable.width), placeable.height) {
                            placeable.placeRelative(0, 0)
                        }
                    }
            ) {
                RichMessage(
                    context = context,
                    list = messages,
                    audioStatus = audioStatus,
                    time = time,
                    modifier = Modifier.widthIn(
                        min = 45.dp,
                        max = with(density) { messageContentWidth.toDp() }
                    ),
                    contentPadding = messages.run {
                        val single = singleOrNull() ?: return@run 8.dp
                        if (
                            single is VisibleChatMessage.Image ||
                            single is VisibleChatMessage.FlashImage ||
                            single is VisibleChatMessage.Audio ||
                            single is VisibleChatMessage.File ||
                            single is VisibleChatMessage.Forward
                        ) return@run 0.dp else return@run 8.dp
                    },
                    onClickAnnotated = { }
                )
            }
        }
    }
}

@Composable
private fun RichMessage(
    context: Context,
    list: List<VisibleChatMessage>,
    audioStatus: ChatAudioStatus?,
    time: String,
    contentPadding: Dp,
    modifier: Modifier = Modifier,
    onClickAnnotated: (VisibleChatMessage) -> Unit,
) {
    val isPrimary = LocalPrimaryMessage.current

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
                    color = MaterialTheme.colorScheme.run { if (isPrimary) inversePrimary else primary },
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }

    Box() {
        FlowRow(
            mainAxisAlignment = if (list.size == 1) MainAxisAlignment.Center else MainAxisAlignment.Start,
            modifier = modifier
                .padding(contentPadding)
                .padding(2.dp)
        ) {
            list.forEach { msg ->
                when (msg) {
                    is VisibleChatMessage.PlainText -> PlainText(msg)
                    is VisibleChatMessage.Image -> Image(msg, context) { }
                    is VisibleChatMessage.At -> At(msg) { }
                    is VisibleChatMessage.AtAll -> AtAll(msg)
                    is VisibleChatMessage.Face -> Face(msg, context)
                    is VisibleChatMessage.FlashImage -> FlashImage(msg, context) { }
                    is VisibleChatMessage.Audio -> {
                        Audio(msg, audioStatus) { }
                    }

                    is VisibleChatMessage.File -> File(msg) { }
                    is VisibleChatMessage.Forward -> {}
                    is VisibleChatMessage.Unsupported -> Unsupported(msg)

                    else -> {}
                }
            }
        }

        /*MessageTimeIndicator(
            color = Color.Black.copy(alpha = 0.3f),
            textPadding = 2.dp,
            mdf = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )*/
    }

    /*if (list.size == 1 && list.singleOrNull().run {
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
        }
    } else {
        Column(modifier = modifier) {

            MessageTimeIndicator(
                color = Color.Transparent,
                mdf = Modifier
                    .align(Alignment.End)
                    .padding(end = 10.dp, bottom = 8.dp)
                    .padding(start = 30.dp)
            )
        }
    }*/
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
