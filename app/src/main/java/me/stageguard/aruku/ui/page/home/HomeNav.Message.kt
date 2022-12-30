package me.stageguard.aruku.ui.page.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.stageguard.aruku.R
import me.stageguard.aruku.service.parcel.ArukuMessageType
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.theme.ArukuTheme
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.random.nextLong

@Composable
fun HomeNavMessage(padding: PaddingValues) {
    val bot = LocalBot.current
    val viewModel: HomeViewModel = koinViewModel()
    LaunchedEffect(bot) {
        bot?.let { viewModel.observeMessagePreview(it) }
    }
    LazyColumn(Modifier.padding(padding)) {
        viewModel.messages.forEach { msg ->
            item(key = msg.type to msg.subject) {
                MessageCard(
                    msg,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun MessageCard(message: SimpleMessagePreview, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier, elevation = CardDefaults.cardElevation(4.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.align(Alignment.CenterStart)) {
                Card(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(35.dp),
                    shape = CircleShape,
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    AsyncImage(
                        ImageRequest.Builder(LocalContext.current)
                            .data(message.avatarData)
                            .crossfade(true)
                            .build(),
                        "message avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Text(
                        text = message.name,
                        modifier = Modifier,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = message.preview,
                        modifier = Modifier,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
            Text(
                text = message.time.run {
                    "${
                        hour.run { if (this < 10) "0$this" else this.toString() }
                    }:${
                        minute.run { if (this < 10) "0$this" else this.toString() }
                    }"
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            )
            Text(
                text = message.unreadCount.toString(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(11.dp)
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape.copy(CornerSize(100))
                    )
                    .padding(3.dp),
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

@Preview
@Composable
fun MessageCardPreview() {
    val mockMessages = buildList {
        for (i in 1..20) {
            this += R.mipmap.ic_launcher to "MockUserName$i"
        }
    }.shuffled().map {
        SimpleMessagePreview(
            ArukuMessageType.GROUP,
            123123L,
            it.first,
            it.second,
            "message preview",
            LocalDateTime.now().minusMinutes(Random.Default.nextLong(0L..3600L)),
            (0..100).random()
        )
    }

    ArukuTheme {
        LazyColumn(modifier = Modifier.width(300.dp)) {
            mockMessages.forEach {
                item {
                    MessageCard(
                        it,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}