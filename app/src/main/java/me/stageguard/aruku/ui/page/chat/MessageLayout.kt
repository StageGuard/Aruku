package me.stageguard.aruku.ui.page.chat

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.valentinilk.shimmer.Shimmer
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.stageguard.aruku.ui.common.ClickableText
import me.stageguard.aruku.ui.theme.surface2
import me.stageguard.aruku.util.LoadState
import me.stageguard.aruku.util.animateFloatAsMutableState
import me.stageguard.aruku.util.formatFileSize
import me.stageguard.aruku.util.getFileIcon
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@SuppressLint("ComposableNaming")
@Composable
fun UIMessageElement.toLayout(
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    textContentColor: Color = MaterialTheme.colorScheme.run {
        if (isPrimary) onSecondary else onSecondaryContainer
    },
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium
    ),
    imageShape: Shape = RoundedCornerShape(2.5.dp),
    quoteBackgroundShape: Shape = imageShape,
    quoteBackgroundColor: Color = MaterialTheme.colorScheme.run {
        if (isPrimary) secondaryContainer else surface2
    },
    audioStatus: ChatAudioStatus? = null,
    quoteStatus: ChatQuoteMessageStatus? = null,
    fileStatus: ChatFileStatus? = null,
    onMeasureTextLayout: (TextLayoutResult) -> Unit = {}
) {
    when (this) {
        is UIMessageElement.AnnotatedText -> AnnotatedText(
            texts = textSlice,
            baseTextColor = textContentColor,
            textStyle = textStyle,
            isPrimary = isPrimary,
            modifier = modifier,
            onTextLayout = onMeasureTextLayout,
            onClick = { },
        )
        is UIMessageElement.Image -> Image(
            element = this,
            shape = imageShape,
            modifier = modifier,
            onClick = {  }
        )
        is UIMessageElement.FlashImage -> FlashImage(
            element = this,
            shape = imageShape,
            modifier = modifier,
            onClick = {  }
        )
        is UIMessageElement.Audio -> Audio(
            element = this,
            status = audioStatus,
            isPrimary = isPrimary,
            modifier = modifier
        ) { }
        is UIMessageElement.File -> File(
            element = this,
            status = fileStatus,
            isPrimary = isPrimary,
            modifier = modifier
        ) { }
        is UIMessageElement.Forward -> {} //TODO
        is UIMessageElement.Quote -> Quote(
            element = this,
            shape = quoteBackgroundShape,
            status = quoteStatus,
            padding = 8.dp,
            bodyTextColor = textContentColor,
            backgroundColor = quoteBackgroundColor,
            modifier = modifier,
        ) {  }
        is UIMessageElement.Unsupported -> Unsupported(this, modifier = modifier)
    }
}

@Composable
fun AnnotatedText(
    texts: List<UIMessageElement.Text>,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    baseTextColor: Color = MaterialTheme.colorScheme.run {
        if (isPrimary) onSecondary else onSecondaryContainer
    },
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    onTextLayout: (TextLayoutResult) -> Unit,
    onClick: (UIMessageElement.Text) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val currentOnClick by rememberUpdatedState(newValue = onClick)
    val textSizeDp = remember(density) { with(density) { 24.dp.toSp() } }
    val inlineFaceMap: MutableMap<String, InlineTextContent> = remember { mutableStateMapOf() }

    val annotatedContent = buildAnnotatedString {
        var length = 0
        texts.forEachIndexed { index, element ->
            if (element is UIMessageElement.Text.Face) {
                val identify by remember { mutableStateOf("[face:${element.id}]") }

                appendInlineContent(identify, identify)
                inlineFaceMap[identify] = InlineTextContent(
                    placeholder = Placeholder(
                        width = textSizeDp,
                        height = textSizeDp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    ),
                    children = { id ->
                        val faceName = id.substringAfter(':')
                            .substringBefore(']')
                            .padStart(3, '0')
                        val faceAsset = "file:///android_asset/face/face_$faceName.apng"

                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(Uri.fromFile(java.io.File(faceAsset)))
                                .crossfade(true)
                                .build(),
                            "chat face",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
                length += identify.length
                return@forEachIndexed
            }
            append(element.text)
            addStyle(
                style = SpanStyle(
                    color = if (element !is UIMessageElement.Text.PlainText) {
                        MaterialTheme.colorScheme.run { if (isPrimary) inversePrimary else primary }
                    } else baseTextColor,
                    fontWeight = if (element !is UIMessageElement.Text.PlainText) FontWeight.SemiBold else null
                ),
                start = length,
                end = length + element.text.length
            )
            if(element !is UIMessageElement.Text.PlainText) {
                addStringAnnotation(
                    tag = "AT",
                    annotation = index.toString(),
                    start = 0,
                    end = length + element.text.length
                )
            }
            length += element.text.length
        }
    }

    ClickableText(
        text = annotatedContent,
        style = if (inlineFaceMap.isEmpty()) textStyle else textStyle.copy(lineHeight = textSizeDp),
        modifier = modifier,
        inlineContent = inlineFaceMap,
        onTextLayout = onTextLayout,
        onClick = {
            val atTargetAnnotation = annotatedContent
                .getStringAnnotations("AT", it, it)
                .firstOrNull()
            if (atTargetAnnotation != null) {
                val index = atTargetAnnotation.item.toIntOrNull() ?: return@ClickableText
                texts.getOrNull(index)?.run(currentOnClick)
            }
        }
    )
}

@Composable
fun Image(
    element: UIMessageElement.Image,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    onClick: (String) -> Unit,
) {
    val context = LocalContext.current
    var width: Dp
    var height: Dp
    if (element.width >= element.height) {
        width = element.width.dp.coerceIn(80.dp, 260.dp)
        height = width * (element.height.toFloat() / element.width)
    } else {
        height = element.height.dp.coerceIn(80.dp, 180.dp)
        width = height * (element.width.toFloat() / element.height)
    }
    if (element.isEmoticons) {
        width *= 0.65f
        height *= 0.65f
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(element.url)
            .crossfade(true)
            .build(),
        "chat image ${element.url}",
        modifier = modifier
            .clip(shape ?: RoundedCornerShape(CornerSize(4.dp)))
            .size(width, height)
            .animateContentSize()
            .then(Modifier.clickable { element.url?.let { onClick(it) } }),
        contentScale = ContentScale.Fit
    )
}

@Composable
fun FlashImage(
    element: UIMessageElement.FlashImage,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    onClick: (url: String) -> Unit,
) { // TODO: flash image visible element, currently same as image
    Image(
        UIMessageElement.Image(element.url, element.uuid, element.width, element.height, false),
        modifier,
        shape,
        onClick = onClick,
    )
}

@Composable
fun Audio(
    element: UIMessageElement.Audio,
    status: ChatAudioStatus?,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    onClick: (identity: String) -> Unit,
) {
    val density = LocalDensity.current

    val width = with(density) { 150.dp.roundToPx().toFloat() }
    val height = with(density) { 50.dp.roundToPx().toFloat() }

    val contentColor = MaterialTheme.colorScheme.run { if (isPrimary) onPrimary else primary }
    val waveLineColor = MaterialTheme.colorScheme.run { if (isPrimary) onPrimaryContainer else primaryContainer }
    val backgroundColor = MaterialTheme.colorScheme.run { if (isPrimary) primary else surfaceVariant }

    val indicatorSize = with(density) { 35.dp.roundToPx().toFloat() }
    val progressCircleSize = run {
        val size = with(density) { 28.dp.roundToPx().toFloat() }
        Size(size, size)
    }
    val progressStrokeWidth = with(density) { 3.dp.roundToPx().toFloat() }

    val indicatorCenterOffset = Offset(height / 2f, height / 2f)
    val progressCircleTLOffset = run {
        val size = progressCircleSize.width / 2f
        indicatorCenterOffset - Offset(size, size)
    }
    var startButtonRadius by animateFloatAsMutableState(with(density) { 8.dp.roundToPx().toFloat() }, tween(500))
    val waveBarHalfHeight = with(density) { 10.dp.roundToPx().toFloat() }
    val waveBarWidth = with(density) { 5.dp.roundToPx().toFloat() }
    val waveBarMargin = with(density) { 2.dp.roundToPx().toFloat() }

    var progressRotateStartAngle by remember { mutableStateOf(0f) }
    var progressSweepAngle by animateFloatAsMutableState(0f, tween(500))
    var startButtonRotateAngle by remember { mutableStateOf(0f) }
    var progressBarAlpha by animateFloatAsMutableState(0f, tween(500))

    val composableScope = rememberCoroutineScope()
    var loadingJob: Job? by remember { mutableStateOf(null) }
    var startButtonResetJob: Job? by remember { mutableStateOf(null) }

    val audioWave = remember { List(12) { Random.nextFloat() } } // TODO: real audio wave

    LaunchedEffect(status) {
        when(status) {
            is ChatAudioStatus.Preparing -> {
                startButtonResetJob?.cancelAndJoin()
                startButtonResetJob = null

                startButtonRadius = with(density) { 6.dp.roundToPx().toFloat() }
                progressBarAlpha = 1f
                progressSweepAngle = (status.progress * 360f).toFloat()

                if (loadingJob == null) loadingJob = composableScope.launch(Dispatchers.IO) {
                    while (isActive) {
                        progressRotateStartAngle = (progressRotateStartAngle + 2f) % 360f
                        startButtonRotateAngle = (startButtonRotateAngle + 3f) % 360f

                        delay(8L) // 让给其他协程
                    }
                }
            }
            else -> {
                val loadingJob0 = loadingJob ?: return@LaunchedEffect
                loadingJob0.cancelAndJoin()
                loadingJob = null

                progressBarAlpha = 0f
                startButtonRadius = with(density) { 8.dp.roundToPx().toFloat() }
                if (startButtonResetJob == null) {
                    startButtonResetJob = composableScope.launch(Dispatchers.IO) {
                        var reminder = startButtonRotateAngle % 120
                        while (reminder < 120 && isActive) {
                            reminder += 3f
                            startButtonRotateAngle = reminder
                            delay(8L)
                        }
                        startButtonRotateAngle = 0f
                    }
                }
            }
        }
    }

    Canvas(modifier = with(density) { modifier.requiredSize(width.toDp(), height.toDp()) }) {
        drawCircle(
            color = contentColor,
            radius = indicatorSize / 2f,
            center = indicatorCenterOffset,
        )

        drawArc(
            color = backgroundColor,
            startAngle = progressRotateStartAngle,
            sweepAngle = progressSweepAngle,
            useCenter = false,
            topLeft = progressCircleTLOffset,
            size = progressCircleSize,
            alpha = progressBarAlpha,
            style = Stroke(
                width = progressStrokeWidth,
                cap = StrokeCap.Round,
            )
        )


        drawPath(
            path = Path().apply {
                moveTo(
                    indicatorCenterOffset.x + startButtonRadius * cos(startButtonRotateAngle * Math.PI / 180f).toFloat(),
                    indicatorCenterOffset.y + startButtonRadius * sin(startButtonRotateAngle * Math.PI / 180f).toFloat()
                )
                lineTo(
                    indicatorCenterOffset.x + startButtonRadius * cos((startButtonRotateAngle - 120f) * Math.PI / 180f).toFloat(),
                    indicatorCenterOffset.y + startButtonRadius * sin((startButtonRotateAngle - 120f) * Math.PI / 180f).toFloat()
                )
                lineTo(
                    indicatorCenterOffset.x + startButtonRadius * cos((startButtonRotateAngle - 240f) * Math.PI / 180f).toFloat(),
                    indicatorCenterOffset.y + startButtonRadius * sin((startButtonRotateAngle - 240f) * Math.PI / 180f).toFloat()
                )
                close()
            },
            color = backgroundColor,
            style = Fill
        )

        translate(height, 0f) {
            audioWave.forEachIndexed { i, w ->
                val waveHalfHeight = w * waveBarHalfHeight
                drawLine(
                    color = contentColor,
                    start = Offset(
                        2 * waveBarMargin + i * (waveBarWidth + waveBarMargin),
                        height / 2 - waveHalfHeight
                    ),
                    end = Offset(
                        2 * waveBarMargin + i * (waveBarWidth + waveBarMargin),
                        height / 2 + waveHalfHeight
                    ),
                    strokeWidth = waveBarWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun File(
    element: UIMessageElement.File,
    status: ChatFileStatus?,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.run {
        if (isPrimary) onSecondary else onSecondaryContainer
    },
    onClick: (url: String) -> Unit,
) { // TODO: audio visible element, currently plain text
    val extension = remember(element) {
        element.extension ?: element.name.split('.').lastOrNull() ?: ""
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.padding(end = 12.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.getFileIcon(extension),
                    contentDescription = "$extension file",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column {
            Text(
                text = element.name,
                style = MaterialTheme.typography.labelLarge,
                color = textColor
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = element.size.formatFileSize(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
fun Quote(
    element: UIMessageElement.Quote,
    shape: Shape,
    status: ChatQuoteMessageStatus?,
    modifier: Modifier = Modifier,
    shimmer: Shimmer = rememberShimmer(shimmerBounds = ShimmerBounds.View),
    padding: Dp = 4.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    bodyTextColor: Color = MaterialTheme.colorScheme.onSurface,
    bodyTextStyle: TextStyle = MaterialTheme.typography.bodySmall,
    onNavigateMessage: (messageId: Long) -> Unit
) {
    val density = LocalDensity.current

    val loading = status is ChatQuoteMessageStatus.Querying
    val message = if (status is ChatQuoteMessageStatus.Ready) status.msg else null

    val lineHeight = with(density) { bodyTextStyle.lineHeight.toDp() }
    val textVertPadding = with(density) {
        (bodyTextStyle.lineHeight.toDp() - bodyTextStyle.fontSize.toDp()) / 2
    }

    var senderName: LoadState<String> by remember { mutableStateOf(LoadState.Loading()) }
    LaunchedEffect(message) {
        if (message != null) senderName = LoadState.Ok(message.senderName())
    }

    @Composable
    fun Modifier.placeholder(width: Dp): Modifier {
        return width(width)
            .height(with(density) { bodyTextStyle.lineHeight.toDp() })
            .padding(textVertPadding)
            .shimmer(shimmer)
            .background(
                color = MaterialTheme.colorScheme.secondary,
                shape = RoundedCornerShape(0)
            )
    }

    Surface(
        shape = shape,
        color = backgroundColor,
        modifier = Modifier
            .animateContentSize()
            .then(modifier)
            .clip(shape)
            .clickable { onNavigateMessage(element.messageId) }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(horizontal = padding)
                    .padding(top = padding)
                    .padding(bottom = lineHeight / 4)
            ) {
                Text(
                    text = (senderName as? LoadState.Ok)?.value ?: "",
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .run { if (!loading && senderName is LoadState.Ok) this else placeholder(80.dp) },
                    style = bodyTextStyle,
                    color = bodyTextColor,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
                Text(
                    text = message?.time ?: "",
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .weight(1.0f, fill = false)
                        .run { if (loading) placeholder(40.dp) else this },
                    style = bodyTextStyle,
                    color = bodyTextColor
                )
            }
            Text(
                text = message?.messages?.contentToString() ?: "",
                modifier = Modifier
                    .padding(horizontal = padding)
                    .padding(bottom = padding)
                    .padding(top = lineHeight / 4)
                    .run { if (loading) placeholder(140.dp) else this },
                style = bodyTextStyle,
                color = bodyTextColor,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )

        }
    }
}

@Composable
fun Unsupported(
    element: UIMessageElement.Unsupported,
    modifier: Modifier = Modifier,
) {
    AnnotatedText(
        listOf(UIMessageElement.Text.PlainText("[Unsupported]${element.content.take(15)}")),
        onTextLayout = {},
        modifier = modifier,
        isPrimary = true,
        onClick = {}
    )
}