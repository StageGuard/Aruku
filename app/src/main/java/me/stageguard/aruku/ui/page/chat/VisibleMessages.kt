package me.stageguard.aruku.ui.page.chat

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.stageguard.aruku.R
import me.stageguard.aruku.util.stringResC
import okhttp3.internal.toLongOrDefault

@Composable
fun PlainText(
    element: VisibleChatMessage.PlainText,
    primary: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = element.content,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        lineHeight = 22.sp,
        onTextLayout = {

        }
    )
}

@Composable
fun Image(
    element: VisibleChatMessage.Image,
    context: Context,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit,
) {
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(element.url)
            .crossfade(true)
            .build(),
        "chat image ${element.url}",
        modifier = modifier.then(Modifier.clickable { element.url?.let { onClick(it) } }),
        contentScale = ContentScale.FillBounds
    )
}

@Composable
fun At(
    element: VisibleChatMessage.At,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: (Long) -> Unit,
) {
    val annotatedContent = buildAnnotatedString {
        append(element.targetName)
        addStyle(
            style = SpanStyle(
                color = if (primary) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            ),
            start = 0,
            end = element.targetName.length
        )
        addStringAnnotation(
            tag = "AT",
            annotation = element.targetId.toString(),
            start = 0,
            end = element.targetName.length
        )
    }
    ClickableText(
        text = annotatedContent,
        modifier = modifier,
        onClick = {
            val atTargetAnnotation =
                annotatedContent.getStringAnnotations("AT", it, it).firstOrNull()
            if (atTargetAnnotation != null) onClick(atTargetAnnotation.item.toLongOrDefault(-1L))
        }
    )
}

@Composable
fun AtAll(
    element: VisibleChatMessage.AtAll,
    primary: Boolean,
    modifier: Modifier = Modifier
) {
    val atAllText = R.string.message_at_all.stringResC
    Text(
        text = buildAnnotatedString {
            append("@${atAllText}")
            addStyle(
                style = SpanStyle(
                    color = if (primary) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                ),
                start = 0,
                end = atAllText.length
            )
        },
        modifier = modifier,
    )
}

@Composable
fun Face(
    element: VisibleChatMessage.Face,
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
    element: VisibleChatMessage.FlashImage,
    context: Context,
    modifier: Modifier = Modifier,
    onClick: (url: String) -> Unit,
) { // TODO: flash image visible element, currently same as image
    Image(
        VisibleChatMessage.Image(element.url),
        context,
        modifier,
        onClick,
    )
}

@Composable
fun Audio(
    element: VisibleChatMessage.Audio,
    status: ChatAudioStatus?,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: (identity: String) -> Unit,
) {
    val density = LocalDensity.current

    val width = with(density) { 150.dp.roundToPx().toFloat() }
    val height = with(density) { 50.dp.roundToPx().toFloat() }

    val contentColor = MaterialTheme.colorScheme.run { if (primary) onPrimary else this.primary }
    val backgroundColor = MaterialTheme.colorScheme.run { if (primary) this.primary else surfaceVariant }

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

    var rotateStartAngle by remember { mutableStateOf(0f) }
    var sweepAngle by remember { mutableStateOf(0f) }

    LaunchedEffect(key1 = element) {
        launch(Dispatchers.IO) {
            while (isActive) {
                rotateStartAngle = (rotateStartAngle + 2f) % 360f
                if (sweepAngle < 180f) sweepAngle += 0.2f
                delay(10) // 让给其他协程
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
            startAngle = rotateStartAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = progressCircleTLOffset,
            size = progressCircleSize,
            style = Stroke(
                width = progressStrokeWidth,
                cap = StrokeCap.Round,
            )
        )
    }
}

@Composable
fun File(
    element: VisibleChatMessage.File,
    modifier: Modifier = Modifier,
    onClick: (url: String) -> Unit,
) { // TODO: audio visible element, currently plain text
    PlainText(
        VisibleChatMessage.PlainText("[File]${element.name}"),
        primary = false,
        modifier = modifier,
    )
}

@Composable
fun Unsupported(
    element: VisibleChatMessage.Unsupported,
    modifier: Modifier = Modifier,
) {
    PlainText(
        VisibleChatMessage.PlainText("[Unsupported]${element.content.take(15)}"),
        primary = false,
        modifier = modifier,
    )
}