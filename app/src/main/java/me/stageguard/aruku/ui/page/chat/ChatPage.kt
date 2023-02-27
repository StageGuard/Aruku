package me.stageguard.aruku.ui.page.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.common.ArrowBack
import me.stageguard.aruku.ui.page.ChatPageNav
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
    val chatAudios = viewModel.chatAudios

    Scaffold(
        modifier = Modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        topBar = {
            TopAppBar(
                navigationIcon = { ArrowBack() },
                title = {
                    ChatTitleBar(
                        name = subjectName,
                        avatarData = subjectAvatar,
                    )
                },
                actions = { ChatTopActions() }
            )
        },
        bottomBar = {
            BottomAppBar(Modifier.height(60.dp)) {
                ChatBar()
            }
        }
    ) { paddingValues ->
        ChatListView(
            chatList = messages.itemSnapshotList.items,
            lazyListState = listState,
            chatAudio = chatAudios,
            paddingValues = paddingValues,
        )
    }

}

