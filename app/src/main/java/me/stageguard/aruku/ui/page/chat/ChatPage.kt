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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.LocalNavController
import me.stageguard.aruku.ui.LocalSystemUiController
import me.stageguard.aruku.ui.common.ArrowBack
import me.stageguard.aruku.ui.page.ChatPageNav
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.ui.theme.surface1
import me.stageguard.aruku.ui.theme.surface4
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.random.Random

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
    val quoteStatus by viewModel.quote.collectAsState(initial = mapOf())
    val fileStatus by viewModel.file.collectAsState(initial = mapOf())

    ChatView(
        subjectName = subjectName,
        subjectAvatar = subjectAvatar,
        messages = messages,
        audioStatus = audioStatus,
        quoteStatus = quoteStatus,
        fileStatus = fileStatus,
        listState = listState,
        onRegisterAudioStatusListener = { viewModel.attachAudioStatusListener(it) },
        onUnRegisterAudioStatusListener = { viewModel.detachAudioStatusListener(it) },
        onQueryQuoteMessage = { viewModel.querySingleMessage(it) },
        onQueryFileStatus = { messageId: Long, fileId: String? ->
            viewModel.queryFileStatus(fileId, messageId)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(
    subjectName: String,
    subjectAvatar: String?,
    messages: LazyPagingItems<ChatElement>,
    audioStatus: Map<String, ChatAudioStatus>,
    quoteStatus: Map<Long, ChatQuoteMessageStatus>,
    fileStatus: Map<Long, ChatFileStatus>,
    listState: LazyListState,
    onRegisterAudioStatusListener: (fileMd5: String) -> Unit,
    onUnRegisterAudioStatusListener: (fileMd5: String) -> Unit,
    onQueryQuoteMessage: suspend (messageId: Long) -> Unit,
    onQueryFileStatus: suspend (messageId: Long, fileId: String?) -> Unit,
) {
    val systemUiController = LocalSystemUiController.current

    val backgroundColor = MaterialTheme.colorScheme.surface1.copy(alpha = 0.95f)
    val navigationContainerColor = MaterialTheme.colorScheme.surface4.copy(alpha = 0.95f)
    val topAppBarColors = TopAppBarDefaults.topAppBarColors(containerColor = navigationContainerColor)

    SideEffect {
        systemUiController.setNavigationBarColor(navigationContainerColor.copy(alpha = 0.13f))
        systemUiController.setStatusBarColor(backgroundColor.copy(alpha = 0.13f))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = backgroundColor,
            modifier = Modifier.fillMaxSize()
        ) {
            Scaffold(
                modifier = Modifier.imePadding(),
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
                    quoteStatus = quoteStatus,
                    fileStatus = fileStatus,
                    lazyListState = listState,
                    paddingValues = paddingValues,
                    onRegisterAudioStatusListener = onRegisterAudioStatusListener,
                    onUnRegisterAudioStatusListener = onUnRegisterAudioStatusListener,
                    onQueryQuoteMessage = onQueryQuoteMessage,
                    onQueryFileStatus = onQueryFileStatus,
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
            val randSrcId = { LongRange(0, 100000000).random() }

            fun text(vararg content: String) = ChatElement.Message(
                senderId = 1425419431,
                senderName = "ojhdt",
                senderAvatarUrl = null,
                time = "12:12",
                messageId = randSrcId(),
                messages = listOf(
                    UIMessageElement.AnnotatedText(
                        content.map { UIMessageElement.Text.PlainText(it) }
                    )
                ),
            )

            val textList = buildList {
                /*val sb = StringBuilder(256)
                repeat(256) {
                    sb.append("t")
                    add(text(sb.toString()))
                }
                add(text("""
                    Apeuriox的个人信息—osu!

                    5534.44pp 表现
                    #47880 (↓32)
                    China #911 (↓1)
                    6,566m Ranked谱面总分
                    98.1% 准确率
                    19844 游玩次数
                    5,793,353 总命中次数
                    412 小时 13 分钟 21 秒游玩时间
                """.trimIndent()))
                add(text("""
                    CIRCLESBHU的个人信息—osu!

                    5003.98pp 表现 (+.51)
                    #66666 (↓22)
                    China #1235
                    8,912m Ranked谱面总分
                    98.74% 准确率 (+)
                    33023 游玩次数 (+100)
                    8,090,934 总命中次数 (+32,789)
                    609 小时 40 分钟 15 秒游玩时间 (+7,400)
                """.trimIndent()))

                add(ChatElement.Message(
                    senderId = 1355416608L,
                    senderName = "StageGuard",
                    senderAvatarUrl = "https://stageguard.top/img/avatar.png",
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.Quote(12345),
                        UIMessageElement.Image(
                            url = "https://gchat.qpic.cn/gchatpic_new/3567284112/914085636-3011562048-BFF2F51051A6B01ABEEFFAFE2496824B/0?term=255&is_origin=1",
                            width = 300,
                            height = 300,
                            uuid = "123",
                            isEmoticons = false,
                        ),
                        UIMessageElement.AnnotatedText(buildList {
                            add(UIMessageElement.Text.PlainText("le2z5ft2.jpg"))
                        })
                    ),
                ))
                add(ChatElement.Message(
                    senderId = 1355416608L,
                    senderName = "StageGuard",
                    senderAvatarUrl = "https://stageguard.top/img/avatar.png",
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.Quote(12345),
                        UIMessageElement.Image(
                            url = "https://gchat.qpic.cn/gchatpic_new/3567284112/914085636-2962512178-41C5E2CCB02B995F4193806463CADF40/0?term=255&is_origin=1",
                            width = 552,
                            height = 904,
                            uuid = "456",
                            isEmoticons = false,
                        ),
                        UIMessageElement.AnnotatedText(buildList {
                            add(UIMessageElement.Text.PlainText("le2z5ft2.jpg"))
                        })
                    ),
                ))
                add(ChatElement.Message(
                    senderId = 1355416608L,
                    senderName = "StageGuard",
                    senderAvatarUrl = "https://stageguard.top/img/avatar.png",
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.AnnotatedText(buildList {
                            add(UIMessageElement.Text.At(123, "某人"))
                            add(UIMessageElement.Text.PlainText("今天你的群老婆是"))
                        }),
                        UIMessageElement.Image(
                            url = "https://gchat.qpic.cn/gchatpic_new/1178264292/4119460545-2779732610-372F20E31A4F7DBED8A95DC45A6D65D4/0?term=255&is_origin=1",
                            width = 640,
                            height = 640,
                            uuid = "789",
                            isEmoticons = false,
                        ),
                        UIMessageElement.AnnotatedText(buildList {
                            add(UIMessageElement.Text.PlainText("游荡的牧师 | lhe_wp(3356639033)哒"))
                        })
                    ),
                ))*/

                /*add(ChatElement.Message(
                    senderId = 1355416608L,
                    senderName = "StageGuard",
                    senderAvatarUrl = null,
                    time = "11:45",
                    messageId = 12345L,
                    messages = listOf(
                        UIMessageElement.AnnotatedText(buildList {
                            add(UIMessageElement.Text.At(123, "某人"))
                            add(UIMessageElement.Text.PlainText("今天你的群老婆是"))
                        }),
                    ),
                ))

                add(ChatElement.Message(
                    senderId = 1355416608L,
                    senderName = "StageGuard",
                    senderAvatarUrl = null,
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.Quote(1234L),
                        UIMessageElement.AnnotatedText(buildList {
                            add(UIMessageElement.Text.PlainText("quote message with plain tail"))
                        })
                    ),
                ))

                add(ChatElement.Message(
                    senderId = 1355416608L,
                    senderName = "StageGuard",
                    senderAvatarUrl = null,
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.Quote(12345L),
                        UIMessageElement.AnnotatedText(buildList {
                            add(UIMessageElement.Text.PlainText("1"))
                        })
                    ),
                ))
                add(ChatElement.Message(
                    senderId = 1355416608L,
                    senderName = "StageGuard",
                    senderAvatarUrl = null,
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.AnnotatedText(listOf(
                            UIMessageElement.Text.Face(10, "buzhidao"),
                            UIMessageElement.Text.Face(12, "?")
                        ))
                    ),
                ))*/
                val msgFileId = randSrcId()
                add(ChatElement.Message(
                    senderId = 132123123L,
                    senderName = "Sender1",
                    senderAvatarUrl = null,
                    time = "11:45",
                    messageId = msgFileId,
                    messages = listOf(
                        UIMessageElement.File(
                            id = "fileId",
                            "resource.zip",
                            "zip",
                            789234,
                            msgFileId,
                        )
                    ),
                ))
            }

            val list = listOf(
                /*ChatElement.DateDivider("Jan 4, 2023"),
                ChatElement.Notification("XXX toggled mute all", listOf()),
                ChatElement.Message(
                    senderId = 1355416608L,
                    senderName = "StageGuard",
                    senderAvatarUrl = "https://stageguard.top/img/avatar.png",
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.PlainText("1")
                    ),
                ),
                ChatElement.Message(
                    senderId = 1355416608L,
                    senderName = "StageGuard",
                    senderAvatarUrl = "https://stageguard.top/img/avatar.png",
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.PlainText("compose chat list view preview")
                    ),
                ),
                ChatElement.Message(
                    senderId = 1355416608L,
                    senderName = "StageGuard",
                    senderAvatarUrl = "https://stageguard.top/img/avatar.png",
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.PlainText(buildString { repeat(20) { append("long message! ") } })
                    ),
                ),
                ChatElement.Message(
                    senderId = 3129693328L,
                    senderName = "WhichWho",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=3129693328&s=0&timestamp=1673582758562",
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.Face(1),
                        UIMessageElement.Face(10),
                        UIMessageElement.PlainText("<- this is face.")
                    ),
                ),
                ChatElement.Message(
                    senderId = 202746796L,
                    senderName = "SIGTERM",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=202746796&s=0&timestamp=1682467820331",
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.Image(width = 100, height = 100, uuid = "", isEmoticons = false,
                            url = "https://gchat.qpic.cn/gchatpic_new/2591482572/2079312506-2210827314-3599E59C0E36C66A966F4DD2E28C4341/0?term=255&is_origin=0")
                    ),
                ),
                ChatElement.Message(
                    senderId = 202746796L,
                    senderName = "SIGTERM",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=202746796&s=0&timestamp=1682467820331",
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.At(3129693328L, "WhichWho"),
                        UIMessageElement.PlainText(" this is my error log"),
                        UIMessageElement.Image(width = 1141, height = 194, uuid = "", isEmoticons = false,
                            url = "https://mirai.mamoe.net/assets/uploads/files/1672243675745-ece9effe-c9eb-4bcb-aba1-529e6f0c5f49-image.png")

                    ),
                ),
                ChatElement.Message(
                    senderId = 1425419431,
                    senderName = "ojhdt",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=1425419431&s=0&timestamp=1681953695785",
                    time = "12:11",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.Image(width = 582, height = 960, uuid = "", isEmoticons = false,
                            url = "https://mirai.mamoe.net/assets/uploads/files/1681003756440-4cebadd6-5a24-40f3-af47-f8a010738af1-r-8-z13_q_p-7yz-on-ah.jpg")
                    ),
                ),
                ChatElement.Message(
                    senderId = 1425419431,
                    senderName = "ojhdt",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=1425419431&s=0&timestamp=1681953695785",
                    time = "12:12",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.PlainText("testestestestestestestestestestestestestestestestestestestestestestestestestest")
                    ),
                ),
                ChatElement.Message(
                    senderId = 1425419431,
                    senderName = "ojhdt",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=1425419431&s=0&timestamp=1681953695785",
                    time = "12:12",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.PlainText("testestestestestestestestestestestestestestestestestestest")
                    ),
                ),
                ChatElement.Message(
                    senderId = 202746796L,
                    senderName = "SIGTERM",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=202746796&s=0&timestamp=1682467820331",
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.At(3129693328L, "WhichWho"),
                        UIMessageElement.PlainText(" this is my e|rror log"),
                        UIMessageElement.Image(width = 1141, height = 194, uuid = "", isEmoticons = false,
                            url = "https://mirai.mamoe.net/assets/uploads/files/1672243675745-ece9effe-c9eb-4bcb-aba1-529e6f0c5f49-image.png")

                    ),
                ),*/
                text("testestestestestestesttestestesttestestestest", "test", "testtest", "testtesttesttest"),
                text("testestestestestesteste"),
                text("testestestestestestestestestestestest"),
                text("testestestestestestestestestestestetestestest"),
                text("testestestestestestestestestestestetestestestestestest"),
                /*ChatElement.Message(
                    senderId = 3129693328L,
                    senderName = "WhichWho",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=3129693328&s=0&timestamp=1673582758562",
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.Audio("audio1", "123123123")
                    ),
                ),
                ChatElement.Message(
                    senderId = 3129693328L,
                    senderName = "WhichWho",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=3129693328&s=0&timestamp=1673582758562",
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.Audio("audio2", "123123123")
                    ),
                ),
                ChatElement.Message(
                    senderId = 3129693328L,
                    senderName = "WhichWho",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=3129693328&s=0&timestamp=1673582758562",
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.Audio("audio3", "123123123")
                    ),
                ),
                ChatElement.Message(
                    senderId = 3129693328L,
                    senderName = "WhichWho",
                    senderAvatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=3129693328&s=0&timestamp=1673582758562",
                    time = "11:45",
                    messageId = randSrcId(),
                    messages = listOf(
                        UIMessageElement.Audio("audio4", "123123123")
                    ),
                ),*/
            )

            val map = remember {
                mutableStateMapOf(
                    "audio1" to ChatAudioStatus.Error("my error"),
                    "audio2" to ChatAudioStatus.NotFound,
                    "audio3" to ChatAudioStatus.NotFound,
                    "audio4" to ChatAudioStatus.Ready(List(20) { Math.random() }),
                )
            }
            val map2 = remember {
                mutableStateMapOf(
                    123L to ChatQuoteMessageStatus.Error("my error"),
                    1234L to ChatQuoteMessageStatus.Querying,
                    12345L to ChatQuoteMessageStatus.Ready(ChatElement.Message(
                        senderId = 1355416608L,
                        senderName = "StageGuard",
                        senderAvatarUrl = null,
                        time = "11:45",
                        messageId = randSrcId(),
                        messages = listOf(
                            UIMessageElement.AnnotatedText(buildList {
                                add(UIMessageElement.Text.At(123, "某人"))
                                add(UIMessageElement.Text.PlainText("今天你的群老婆是"))
                            }),
                            /*UIMessageElement.Image(
                                url = "https://gchat.qpic.cn/gchatpic_new/1178264292/4119460545-2779732610-372F20E31A4F7DBED8A95DC45A6D65D4/0?term=255&is_origin=1",
                                width = 640,
                                height = 640,
                                uuid = "789",
                                isEmoticons = false,
                            ),
                            UIMessageElement.Image(
                                url = "https://gchat.qpic.cn/gchatpic_new/1178264292/4119460545-2779732610-372F20E31A4F7DBED8A95DC45A6D65D4/0?term=255&is_origin=1",
                                width = 640,
                                height = 640,
                                uuid = "789",
                                isEmoticons = false,
                            ),*//*
                            UIMessageElement.AnnotatedText(buildList {
                                add(UIMessageElement.Text.PlainText("游荡的牧师 | lhe_wp(3356639033)哒"))
                            })*/
                        ),
                    )),
                )
            }

            val map3 = remember {
                mutableStateMapOf(
                    12345678L to ChatFileStatus.Operational("")
                )
            }

            LaunchedEffect(key1 = Unit, block = {
                map["audio2"] = ChatAudioStatus.Preparing(0.0)
                var progress = 0.0
                while (progress < 1.0) {
                    delay(Random.nextLong(500, 1000))
                    progress = (progress + Random.nextDouble(0.4)).coerceAtMost(1.0)
                    map["audio2"] = ChatAudioStatus.Preparing(progress)
                }
                map["audio2"] = ChatAudioStatus.Preparing(1.0)
                map["audio2"] = ChatAudioStatus.Ready(listOf())
            })

            ChatView(subjectName = "Group1",
                subjectAvatar = null,
                messages = flow {
                    emit(PagingData.from(textList.asReversed() as List<ChatElement>))
                }.collectAsLazyPagingItems(),
                audioStatus = map,
                quoteStatus = map2,
                fileStatus = map3,
                listState = state,
                {}, {}, {}, { _, _ -> }
            )
        }
    }

}