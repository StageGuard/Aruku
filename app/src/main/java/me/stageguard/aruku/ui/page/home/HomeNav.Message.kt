package me.stageguard.aruku.ui.page.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.stageguard.aruku.R
import me.stageguard.aruku.ui.theme.ArukuTheme
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextLong

@Composable
fun HomeNavMessage(padding: PaddingValues) {
    val viewModel: HomeViewModel = viewModel()
    LazyColumn(Modifier.padding(padding)) {
        viewModel.messages.forEach {
            item {
                MessageCard(it, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp).fillMaxWidth())
            }
        }
    }
}

@Composable
fun MessageCard(message: Message, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier, elevation = CardDefaults.cardElevation(4.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.align(Alignment.CenterStart)) {
                Card(
                    modifier = Modifier.padding(12.dp).size(35.dp),
                    shape = CircleShape,
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Image(
                        message.avatar,
                        "message avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(modifier = Modifier.padding(start = 2.dp).align(Alignment.CenterVertically)) {
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
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
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
                    ).padding(3.dp),
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


private val mockMessages = listOf(
    R.drawable.ic_mock_avatar_1 to "MockUserName1",
    R.drawable.ic_mock_avatar_2 to "MockUserName2",
    R.drawable.ic_mock_avatar_3 to "MockUserName3",
    R.drawable.ic_mock_avatar_4 to "MockUserName4",
    R.drawable.ic_mock_avatar_5 to "MockUserName5",
    R.drawable.ic_mock_avatar_6 to "MockUserName6",
    R.drawable.ic_mock_avatar_7 to "MockUserName7",
    R.drawable.ic_mock_avatar_8 to "MockUserName8",
    R.drawable.ic_mock_avatar_9 to "MockUserName9",
    R.drawable.ic_mock_avatar_10 to "MockUserName10",
    R.drawable.ic_mock_avatar_11 to "MockUserName11",
    R.drawable.ic_mock_avatar_12 to "MockUserName12",
    R.drawable.ic_mock_avatar_13 to "MockUserName13",
    R.drawable.ic_mock_avatar_14 to "MockUserName14",
    R.drawable.ic_mock_avatar_15 to "MockUserName15",
    R.drawable.ic_mock_avatar_16 to "MockUserName16",
    R.drawable.ic_mock_avatar_17 to "MockUserName17",
    R.drawable.ic_mock_avatar_18 to "MockUserName18",
)

@Composable
private fun provideMockMessages(): List<Message> {
    return mockMessages.shuffled().map {
        Message(
            ImageBitmap.imageResource(it.first),
            it.second,
            "message preview",
            LocalDateTime.now().minusMinutes(Random.Default.nextLong(0L..3600L)),
            Random.Default.nextInt(0..100)
        )
    }
}

@Preview
@Composable
fun MessageCardPreview() {
    val mockMessages = provideMockMessages()
    ArukuTheme {
        LazyColumn(modifier = Modifier.width(300.dp)) {
            mockMessages.forEach {
                item {
                    MessageCard(it, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp).fillMaxWidth())
                }
            }
        }
    }
}