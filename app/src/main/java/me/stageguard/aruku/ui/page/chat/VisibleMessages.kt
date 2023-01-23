package me.stageguard.aruku.ui.page.chat

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
        style = MaterialTheme.typography.bodyLarge.copy(
            color = LocalContentColor.current
        )
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
        modifier = modifier.then(Modifier.clickable { element.url?.let { onClick(it) } })
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
            end = element.targetName.length + 1
        )
        addStringAnnotation(
            tag = "AT",
            annotation = element.targetId.toString(),
            start = 0,
            end = element.targetName.length + 1
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
    status: State<ChatAudioStatus>?,
    modifier: Modifier = Modifier,
    onClick: (url: String) -> Unit,
) { // TODO: audio visible element, currently plain text
    PlainText(
        VisibleChatMessage.PlainText(buildString {
            append("[Audio]")
            append("[")
            if (status == null) append("NotPrepared") else when (val v = status.value) {
                is ChatAudioStatus.Unknown -> append("NotPrepared")
                is ChatAudioStatus.Preparing -> append("Preparing:").append(v.progress)
                is ChatAudioStatus.NotFound -> append("NotFound")
                is ChatAudioStatus.Ready -> append("Ready")
            }
            append("]")
            append(element.name)
        }),
        primary = false,
        modifier = modifier,
    )
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