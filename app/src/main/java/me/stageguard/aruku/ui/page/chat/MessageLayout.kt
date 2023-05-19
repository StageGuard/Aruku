package me.stageguard.aruku.ui.page.chat

import android.content.Context
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.stageguard.aruku.ui.LocalPrimaryMessage
import me.stageguard.aruku.util.animateFloatAsMutableState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun AnnotatedText(
    texts: List<UIMessageElement.Text>,
    modifier: Modifier = Modifier,
    baseTextColor: Color = MaterialTheme.colorScheme.run {
        if (LocalPrimaryMessage.current) onSecondary else onSecondaryContainer
    },
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    onTextLayout: (TextLayoutResult) -> Unit,
    onClick: (UIMessageElement.Text) -> Unit
) {
    val isPrimary = LocalPrimaryMessage.current
    val currentOnClick by rememberUpdatedState(newValue = onClick)

    val annotatedContent = buildAnnotatedString {
        var length = 0
        texts.forEachIndexed { index, element ->
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
        style = textStyle,
        modifier = modifier,
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
    context: Context,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    onClick: (String) -> Unit,
) {
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
        contentScale = ContentScale.FillBounds
    )
}

@Composable
fun Face(
    element: UIMessageElement.Face,
    context: Context,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(
                Uri.parse(
                    "file:///android_asset/face/face_${
                        element.id.toString().padStart(3, '0')
                    }.apng"
                )
            )
            .crossfade(true)
            .build(),
        "chat face",
        modifier = modifier
    )
}

@Composable
fun FlashImage(
    element: UIMessageElement.FlashImage,
    context: Context,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    onClick: (url: String) -> Unit,
) { // TODO: flash image visible element, currently same as image
    Image(
        UIMessageElement.Image(element.url, element.uuid, element.width, element.height, false),
        context,
        modifier,
        shape,
        onClick = onClick,
    )
}

@Composable
fun Audio(
    element: UIMessageElement.Audio,
    status: ChatAudioStatus?,
    modifier: Modifier = Modifier,
    onClick: (identity: String) -> Unit,
) {
    val density = LocalDensity.current
    val isPrimary = LocalPrimaryMessage.current

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
    modifier: Modifier = Modifier,
    onClick: (url: String) -> Unit,
) { // TODO: audio visible element, currently plain text
    AnnotatedText(
        listOf(UIMessageElement.Text.PlainText("[File]${element.name}")),
        onTextLayout = {},
        modifier = modifier,
        onClick = {}
    )
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
        onClick = {}
    )
}