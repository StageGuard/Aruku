package me.stageguard.aruku.ui.page.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.flow.flow
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.LocalNavController
import me.stageguard.aruku.ui.LocalSystemUiController
import me.stageguard.aruku.ui.common.ArrowBack
import me.stageguard.aruku.ui.page.ChatPageNav
import me.stageguard.aruku.ui.theme.ArukuTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Created by LoliBall on 2023/1/1 19:30.
 * https://github.com/WhichWho
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPage(contact: ChatPageNav) {
    val bot = LocalBot.current
    val viewModel: ChatViewModel = koinViewModel { parametersOf(contact, bot) }

    val subjectName by viewModel.subjectName.collectAsState()
    val subjectAvatar by viewModel.subjectAvatar.collectAsState()
    val listState = rememberLazyListState()

    val messages = viewModel.messages.collectAsLazyPagingItems()
    val audioStatus by viewModel.audio.collectAsState(initial = mapOf())

    ChatView(
        subjectName = subjectName,
        subjectAvatar = subjectAvatar,
        messages = messages,
        audioStatus = audioStatus,
        listState = listState,
        onRegisterAudioStatusListener = { viewModel.attachAudioStatusListener(it) },
        onUnRegisterAudioStatusListener = { viewModel.detachAudioStatusListener(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(
    subjectName: String,
    subjectAvatar: String?,
    messages: LazyPagingItems<ChatElement>,
    audioStatus: Map<String, ChatAudioStatus>,
    listState: LazyListState,
    onRegisterAudioStatusListener: (fileMd5: String) -> Unit,
    onUnRegisterAudioStatusListener: (fileMd5: String) -> Unit,
) {
    val systemUiController = LocalSystemUiController.current

    val backgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.95f)
    val navigationContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp).copy(alpha = 0.95f)
    val topAppBarColors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)

    val scrollState = TopAppBarDefaults.pinnedScrollBehavior()

    SideEffect {
        systemUiController.setNavigationBarColor(navigationContainerColor.copy(alpha = 0.13f))
        systemUiController.setStatusBarColor(backgroundColor.copy(alpha = 0.13f))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize()
        ) {
            Scaffold(
                modifier = Modifier
                    .imePadding()
                    .nestedScroll(scrollState.nestedScrollConnection),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.navigationBars),
                topBar = {
                    TopAppBar(
                        navigationIcon = { ArrowBack() },
                        title = {
                            ChatTitleBar(
                                name = subjectName,
                                avatarData = subjectAvatar,
                            )
                        },
                        actions = { ChatTopActions() },
                        colors = topAppBarColors,
                        scrollBehavior = scrollState
                    )
                },
                bottomBar = {
                    val systemNavPadding = WindowInsets.navigationBars.asPaddingValues()
                    BottomAppBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = navigationContainerColor,
                        modifier = Modifier.height(systemNavPadding.calculateBottomPadding() + 60.dp)
                    ) {
                        ChatBar()
                    }
                }
            ) { paddingValues ->
                ChatListView(
                    chatList = messages,
                    audioStatus = audioStatus,
                    lazyListState = listState,
                    paddingValues = paddingValues,
                    onRegisterAudioStatusListener = onRegisterAudioStatusListener,
                    onUnRegisterAudioStatusListener = onUnRegisterAudioStatusListener
                )
            }
        }
    }
}

@Composable
@Preview
fun ChatViewPreview() {
    ArukuTheme {
        CompositionLocalProvider(
            LocalBot provides 202746796,
            LocalNavController provides rememberNavController(),
            LocalSystemUiController provides rememberSystemUiController()
        ) {
            val state = rememberLazyListState()
            val randSrcId = { IntRange(0, 100000).random() }

            val list = listOf(
                ChatElement.DateDivider("Jan 4, 2023"),
                ChatElement.Notification("XXX toggled mute all", listOf()),
                ChatElement.Message(
                    senderId = 1355416608L,
                    senderName = "StageGuard",
                    senderAvatarUrl = "https://stageguard.top/img/avatar.png",
                    time = "11:45",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.PlainText("1")
                    ),
                ),
                ChatElement.Message(
                    senderId = 1355416608L,
                    senderName = "StageGuard",
                    senderAvatarUrl = "https://stageguard.top/img/avatar.png",
                    time = "11:45",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.PlainText("compose chat list view preview")
                    ),
                ),
                ChatElement.Message(
                    senderId = 1355416608L,
                    senderName = "StageGuard",
                    senderAvatarUrl = "https://stageguard.top/img/avatar.png",
                    time = "11:45",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.PlainText(buildString { repeat(20) { append("long message! ") } })
                    ),
                ),
                ChatElement.Message(
                    senderId = 3129693328L,
                    senderName = "WhichWho",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=3129693328&s=0&timestamp=1673582758562",
                    time = "11:45",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.Face(1),
                        VisibleChatMessage.Face(10),
                        VisibleChatMessage.PlainText("<- this is face.")
                    ),
                ),
                ChatElement.Message(
                    senderId = 202746796L,
                    senderName = "SIGTERM",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=202746796&s=0&timestamp=1682467820331",
                    time = "11:45",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.Image("https://gchat.qpic.cn/gchatpic_new/2591482572/2079312506-2210827314-3599E59C0E36C66A966F4DD2E28C4341/0?term=255&is_origin=0")
                    ),
                ),
                ChatElement.Message(
                    senderId = 202746796L,
                    senderName = "SIGTERM",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=202746796&s=0&timestamp=1682467820331",
                    time = "11:45",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.At(3129693328L, "WhichWho"),
                        VisibleChatMessage.PlainText(" this is my error log"),
                        VisibleChatMessage.Image("https://mirai.mamoe.net/assets/uploads/files/1672243675745-ece9effe-c9eb-4bcb-aba1-529e6f0c5f49-image.png")

                    ),
                ),
                ChatElement.Message(
                    senderId = 1425419431,
                    senderName = "ojhdt",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=1425419431&s=0&timestamp=1681953695785",
                    time = "12:11",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.Image("https://mirai.mamoe.net/assets/uploads/files/1681003756440-4cebadd6-5a24-40f3-af47-f8a010738af1-r-8-z13_q_p-7yz-on-ah.jpg")
                    ),
                ),
                ChatElement.Message(
                    senderId = 1425419431,
                    senderName = "ojhdt",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=1425419431&s=0&timestamp=1681953695785",
                    time = "12:12",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.PlainText("testestestestestestestestestestestestestestestestestestestestestestestestestest")
                    ),
                ),
                ChatElement.Message(
                    senderId = 1425419431,
                    senderName = "ojhdt",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=1425419431&s=0&timestamp=1681953695785",
                    time = "12:12",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.PlainText("testestestestestestestestestestestestestestestestestestest")
                    ),
                ),
                ChatElement.Message(
                    senderId = 202746796L,
                    senderName = "SIGTERM",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=202746796&s=0&timestamp=1682467820331",
                    time = "11:45",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.At(3129693328L, "WhichWho"),
                        VisibleChatMessage.PlainText(" this is my error log"),
                        VisibleChatMessage.Image("https://mirai.mamoe.net/assets/uploads/files/1672243675745-ece9effe-c9eb-4bcb-aba1-529e6f0c5f49-image.png")

                    ),
                ),
                ChatElement.Message(
                    senderId = 1425419431,
                    senderName = "ojhdt",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=1425419431&s=0&timestamp=1681953695785",
                    time = "12:12",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.PlainText("testestestestestestestestestestestest")
                    ),
                ),
                ChatElement.Message(
                    senderId = 1425419431,
                    senderName = "ojhdt",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=1425419431&s=0&timestamp=1681953695785",
                    time = "12:12",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.PlainText("testestestestestestestestestest")
                    ),
                ),
                ChatElement.Message(
                    senderId = 3129693328L,
                    senderName = "WhichWho",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=3129693328&s=0&timestamp=1673582758562",
                    time = "11:45",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.Audio("audio1", "123123123")
                    ),
                ),
                ChatElement.Message(
                    senderId = 3129693328L,
                    senderName = "WhichWho",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=3129693328&s=0&timestamp=1673582758562",
                    time = "11:45",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.Audio("audio2", "123123123")
                    ),
                ),
                ChatElement.Message(
                    senderId = 3129693328L,
                    senderName = "WhichWho",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=3129693328&s=0&timestamp=1673582758562",
                    time = "11:45",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.Audio("audio3", "123123123")
                    ),
                ),
                ChatElement.Message(
                    senderId = 3129693328L,
                    senderName = "WhichWho",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=3129693328&s=0&timestamp=1673582758562",
                    time = "11:45",
                    messageId = randSrcId(),
                    visibleMessages = listOf(
                        VisibleChatMessage.Audio("audio4", "123123123")
                    ),
                ),
            )

            ChatView(subjectName = "Group1",
                subjectAvatar = "https://q1.qlogo.cn/g?b=qq&nk=3129693328&s=0&timestamp=1673582758562",
                messages = flow { emit(PagingData.from(list.asReversed())) }.collectAsLazyPagingItems(),
                audioStatus = mapOf(
                    "audio1" to ChatAudioStatus.Error("my error"),
                    "audio2" to ChatAudioStatus.NotFound,
                    "audio3" to ChatAudioStatus.Preparing(0.48),
                    "audio4" to ChatAudioStatus.Ready(List(20) { Math.random() }),
                ),
                listState = state,
                {}, {}
            )
        }
    }

}