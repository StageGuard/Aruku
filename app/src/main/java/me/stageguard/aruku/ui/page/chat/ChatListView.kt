package me.stageguard.aruku.ui.page.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import me.stageguard.aruku.ui.common.CoerceWidthLayout
import me.stageguard.aruku.ui.theme.surface2
import kotlin.math.min

@Composable
fun ChatListView(
    chatList: LazyPagingItems<ChatElement>,
    audioStatus: Map<String, ChatAudioStatus>,
    quoteStatus: Map<Long, ChatQuoteMessageStatus>,
    fileStatus: Map<Long, ChatFileStatus>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
    onRegisterAudioStatusListener: (fileMd5: String) -> Unit,
    onUnRegisterAudioStatusListener: (fileMd5: String) -> Unit,
    onQueryQuoteMessage: (messageId: Long) -> Unit,
    onQueryFileStatus: (messageId: Long, fileId: String?) -> Unit
) {
    val bot = LocalBot.current

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
                        val audio = element.messages.filterIsInstance<UIMessageElement.Audio>().firstOrNull()
                        if (audio != null) DisposableEffect(key1 = this) {
                            onRegisterAudioStatusListener(audio.identity)
                            onDispose { onUnRegisterAudioStatusListener(audio.identity) }
                        }
                        // query quote message
                        val quote = element.messages.filterIsInstance<UIMessageElement.Quote>().firstOrNull()
                        if (quote != null) onQueryQuoteMessage(quote.messageId)
                        // query file message
                        val file = element.messages.filterIsInstance<UIMessageElement.File>().firstOrNull()
                        if (file != null) onQueryFileStatus(file.correspondingMessageId, file.id)

                        val sentByBot = element.senderId == bot
                        CompositionLocalProvider(LocalPrimaryMessage provides sentByBot) {
                            Message(
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
                                messages = element.messages,
                                audioStatus = audio?.run { audioStatus[identity] },
                                quoteStatus = quote?.run { quoteStatus[messageId] },
                                fileStatus = file?.run { fileStatus[correspondingMessageId] },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = if (nextSentByCurrent) 2.dp else 8.dp)
                                ,
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
    messages: List<UIMessageElement>,
    audioStatus: ChatAudioStatus?,
    quoteStatus: ChatQuoteMessageStatus?,
    fileStatus: ChatFileStatus?,
    modifier: Modifier = Modifier,
    onClickAvatar: (Long) -> Unit,
) {
    if (messages.isEmpty()) return // ??

    val context = LocalContext.current
    val density = LocalDensity.current
    val isPrimary = LocalPrimaryMessage.current

    val avatarSize = 40.dp
    val avatarMargin = 6.dp
    val messageContentSideMargin = 45.dp
    val roundCornerSize = 16.dp

    val avatarWidth = remember(density, showAvatar) {
        if (showAvatar || occupyAvatarSpace) with(density) { (avatarSize + avatarMargin).roundToPx() } else 0
    }

    val bubbleBackgroundColor = MaterialTheme.colorScheme.run { if (isPrimary) secondary else secondaryContainer }
    val textContentColor = MaterialTheme.colorScheme.run { if (isPrimary) onSecondary else onSecondaryContainer }

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
                model = ImageRequest.Builder(context)
                    .data(senderAvatar)
                    .crossfade(true)
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
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.layoutId("senderName")
            )

            val topStart = if (startCorner) roundCornerSize else if (topCorner) roundCornerSize else 4.dp
            val topEnd = if (endCorner) roundCornerSize else if (topCorner) roundCornerSize else 4.dp
            val bottomStart = if (endCorner) roundCornerSize else if (bottomCorner) roundCornerSize else 4.dp
            val bottomEnd = if (startCorner) roundCornerSize else if (bottomCorner) roundCornerSize else 4.dp

            Surface(
                color = bubbleBackgroundColor,
                shape = RoundedCornerShape(topStart, topEnd, bottomStart, bottomEnd),
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
                    message = messages,
                    textContentColor = textContentColor,
                    textContentStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    audioStatus = audioStatus,
                    quoteStatus = quoteStatus,
                    fileStatus = fileStatus,
                    time = time,
                    modifier = Modifier.widthIn(
                        max = with(density) { messageContentWidth.toDp() }
                    ),
                    commonImageShape = RoundedCornerShape(2.5.dp),
                    singleImageShape = RoundedCornerShape(
                        topStart - 1.5.dp,
                        topEnd - 1.5.dp,
                        bottomStart - 1.5.dp,
                        bottomEnd - 1.5.dp
                    ),
                    onClickAnnotated = { }
                )
            }
        }
    }
}

@Composable
private fun RichMessage(
    message: List<UIMessageElement>,
    textContentColor: Color,
    textContentStyle: TextStyle,
    audioStatus: ChatAudioStatus?,
    quoteStatus: ChatQuoteMessageStatus?,
    fileStatus: ChatFileStatus?,
    time: String,
    commonImageShape: CornerBasedShape,
    singleImageShape: CornerBasedShape,
    modifier: Modifier = Modifier,
    onClickAnnotated: (UIMessageElement) -> Unit,
) {
    val isPrimary = LocalPrimaryMessage.current
    val density = LocalDensity.current

    val contentPadding = 8.dp

    @Composable
    fun MessageTimeIndicator(
        backgroundColor: Color,
        textColor: Color,
        textPadding: Dp = 0.dp,
        mdf: Modifier = Modifier
    ) {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(5.dp),
            modifier = mdf
        ) {
            Text(
                text = time,
                modifier = Modifier.padding(textPadding),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                ),
                textAlign = TextAlign.End
            )
        }
    }

    @Composable
    fun BoxScope.ImageMessageTimeIndicator() = MessageTimeIndicator(
        backgroundColor = Color.Black.copy(alpha = 0.5f),
        textColor = MaterialTheme.colorScheme.surface,
        textPadding = 2.dp,
        mdf = Modifier
            .align(Alignment.BottomEnd)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )

    @Composable
    fun CommonMessageTimeIndicator(mdf: Modifier = Modifier) = MessageTimeIndicator(
        backgroundColor = Color.Transparent,
        textColor = MaterialTheme.colorScheme.run { if (isPrimary) secondaryContainer else secondary },
        mdf = mdf
    )

    @Composable
    fun UIMessageElement.toLayout(
        elementModifier: Modifier? = null,
        imageShape: Shape = commonImageShape,
        quoteBackgroundShape: Shape = imageShape,
        onMeasureTextLayout: (TextLayoutResult) -> Unit = {}
    ) {
        when (this) {
            is UIMessageElement.AnnotatedText -> AnnotatedText(
                texts = textSlice,
                baseTextColor = textContentColor,
                textStyle = textContentStyle,
                modifier = elementModifier ?: Modifier,
                onTextLayout = onMeasureTextLayout,
                onClick = { },
            )
            is UIMessageElement.Image -> Image(
                element = this,
                shape = imageShape,
                modifier = elementModifier ?: Modifier,
                onClick = {  }
            )
            is UIMessageElement.FlashImage -> FlashImage(
                element = this,
                shape = imageShape,
                modifier = elementModifier ?: Modifier,
                onClick = {  }
            )
            is UIMessageElement.Audio -> Audio(
                element = this,
                status = audioStatus,
                modifier = elementModifier ?: Modifier
            ) { }
            is UIMessageElement.File -> File(
                element = this,
                status = fileStatus,
                modifier = elementModifier ?: Modifier
            ) { }
            is UIMessageElement.Forward -> {} //TODO
            is UIMessageElement.Quote -> Quote(
                element = this,
                shape = quoteBackgroundShape,
                status = quoteStatus,
                padding = 8.dp,
                backgroundColor = MaterialTheme.colorScheme.run {
                    if (isPrimary) secondaryContainer else surface2
                },
                modifier = elementModifier ?: Modifier,
            ) {  }
            is UIMessageElement.Unsupported -> Unsupported(this,
                modifier = elementModifier ?: Modifier)
        }
    }

    @Composable
    fun TextWithAdaptedTimeIndicator(
        annotatedText: UIMessageElement.AnnotatedText,
        textModifier: Modifier = Modifier,
        coerceMinWidth: Dp = 0.dp,
    ) {

        var lastLineWidth by remember { mutableStateOf(0) }
        var lineCount by remember { mutableStateOf(1) }

        SubcomposeLayout { it ->
            // override constraint min width is necessary for
            // calculating location of time indicator.
            val constraints = it.copy(minWidth = 0)

            val text = subcompose(SlotId.Text) {
                annotatedText.toLayout(
                    elementModifier = textModifier,
                    onMeasureTextLayout = {
                        lineCount = it.lineCount
                        val lastLine = it.lineCount - 1
                        lastLineWidth = (it.getLineRight(lastLine) - it.getLineLeft(lastLine)).toInt()
                    }
                )
            }.single().measure(constraints)

            val timeIndicator = subcompose(SlotId.TimeIndicator) {
                CommonMessageTimeIndicator()
            }.single().measure(constraints)

            if(lastLineWidth == 0) return@SubcomposeLayout layout(text.width, text.height) {
                text.placeRelative(0, 0)
            }

            val padding = with(density) { (contentPadding + 2.dp).roundToPx() }
            val spacing = with(density) { 8.dp.roundToPx() }
            val indicatorYOffset = with(density) { 5.dp.roundToPx() }
            val lastLineHorizontalWidth = 2 * padding + lastLineWidth + spacing + timeIndicator.width
            // text.width already contains horizontal padding
            val layoutHorizontalWidth = text.width + spacing + timeIndicator.width

            val expandWidth = (layoutHorizontalWidth <= constraints.maxWidth) ||
                    lineCount == 1 && lastLineHorizontalWidth <= constraints.maxWidth
            val expandHeight = lastLineHorizontalWidth > constraints.maxWidth

            val coerceMinWidthPx = with(density) { coerceMinWidth.roundToPx() }
            val actualWidth = text.width + if (expandWidth) (timeIndicator.width + spacing) else 0
            val indicatorXOffset = if (coerceMinWidthPx > actualWidth) coerceMinWidthPx - actualWidth else 0

            layout(
                actualWidth + indicatorXOffset,
                text.height + if (expandHeight) timeIndicator.height else 0
            ) {
                text.placeRelative(0, 0)
                timeIndicator.placeRelative(
                    text.width - padding + indicatorXOffset + if (expandWidth) spacing else -timeIndicator.width,
                    text.height - padding + indicatorYOffset + if (expandHeight) 0 else -timeIndicator.height
                )
            }
        }
    }

    @Composable
    fun MessageFlowRow(
        elements: List<UIMessageElement>,
        elementModifier: Modifier = Modifier
    ) {
        if (elements.isEmpty()) return
        SubcomposeLayout { constraints ->
            val placeable = subcompose(SlotId.Other) {
                if (elements.size == 1) {
                    elements.single().toLayout(elementModifier = elementModifier)
                    return@subcompose
                }
                FlowRow(
                    modifier = elementModifier,
                    mainAxisAlignment = MainAxisAlignment.Start
                ) {
                    elements.forEach { it.toLayout() }
                }
            }.single().measure(constraints.copy(minWidth = 0))

            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        }
    }

    // this composable should be used in [CoerceWidthLayout]
    @Composable
    fun QuoteOrNormalFlowRow(
        elements: List<UIMessageElement>,
        elementModifier: Modifier = Modifier,
    ) {
        val first = message.first()
        // first is quote
        if (first is UIMessageElement.Quote) {
            val remain = elements.drop(1)
            // quote
            first.toLayout(
                elementModifier = elementModifier,
                quoteBackgroundShape = singleImageShape.copy(
                    bottomStart = commonImageShape.bottomStart,
                    bottomEnd = commonImageShape.bottomEnd
                )
            )
            MessageFlowRow(
                elements = remain,
                elementModifier = elementModifier
            )
        } else {
            MessageFlowRow(
                elements = elements,
                elementModifier = elementModifier
            )
        }
    }

    /**
     * message row starts
     */

    Box(modifier = modifier) {  // box to constrain size
        val single = message.singleOrNull()
        when {
            // single image
            single != null && single.isImage() -> {
                single.toLayout(
                    elementModifier = Modifier
                        .padding(contentPadding + 2.dp),
                    imageShape = singleImageShape
                )
                ImageMessageTimeIndicator()
            }
            // single text
            single != null && single is UIMessageElement.AnnotatedText -> {
                TextWithAdaptedTimeIndicator(
                    annotatedText = single,
                    textModifier = Modifier.padding(contentPadding + 2.dp)
                )
            }
            // two or more message elements
            else -> {
                val last = message.lastOrNull() ?: return@Box
                val elementModifier = Modifier
                    .padding(horizontal = contentPadding + 2.dp)
                    .padding(top = contentPadding + 2.dp)

                // last message is annotated text
                if (last is UIMessageElement.AnnotatedText) {
                    CoerceWidthLayout { remeasuredWidth: Dp? ->
                        QuoteOrNormalFlowRow(
                            elements = message.dropLast(1),
                            elementModifier = elementModifier,
                        )
                        TextWithAdaptedTimeIndicator(
                            annotatedText = last,
                            textModifier = Modifier.padding(contentPadding + 2.dp),
                            coerceMinWidth = remeasuredWidth ?: 0.dp
                        )
                    }
                } else {
                    // last is not annotated text
                    CoerceWidthLayout {
                        QuoteOrNormalFlowRow(
                            elements = message,
                            elementModifier = elementModifier,
                        )
                        CommonMessageTimeIndicator(
                            mdf = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(horizontal = contentPadding + 2.dp)
                                .padding(vertical = contentPadding / 2)
                        )
                    }
                }
            }
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

private enum class SlotId { Text, TimeIndicator, Other, TextWithIndicator }