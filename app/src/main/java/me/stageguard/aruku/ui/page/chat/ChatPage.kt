package me.stageguard.aruku.ui.page.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.common.ArrowBack
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Created by LoliBall on 2023/1/1 19:30.
 * https://github.com/WhichWho
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPage(contact: ArukuContact) {
    val bot = LocalBot.current
    val viewModel: ChatViewModel = koinViewModel { parametersOf(contact) }

    val avatarData by remember { viewModel.subjectAvatar }
    val listState = rememberLazyListState()

    val messages = viewModel.messages.value?.collectAsLazyPagingItems()
    val chatAudios = viewModel.chatAudios

    LaunchedEffect(bot) {
        if (bot != null) viewModel.init(bot)
    }

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
                        name = viewModel.subjectName.value,
                        avatarData = avatarData,
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
            chatList = messages?.itemSnapshotList,
            lazyListState = listState,
            chatAudio = chatAudios,
            paddingValues = paddingValues,
        )
    }

}

