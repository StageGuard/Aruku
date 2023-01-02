package me.stageguard.aruku.ui.page.home.contact

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import me.stageguard.aruku.ui.LocalBot
import org.koin.androidx.compose.koinViewModel

/**
 * Created by LoliBall on 2022/12/31 11:57.
 * https://github.com/WhichWho
 */
@Composable
fun ContactPage(padding: PaddingValues) {
    val bot = LocalBot.current
    val viewModel: ContactViewModel = koinViewModel()

    val groups = viewModel.groups.value?.collectAsLazyPagingItems()
    val friends = viewModel.friends.value?.collectAsLazyPagingItems()

    LaunchedEffect(bot) { if (bot != null) viewModel.initContacts(bot) }

    Text("!23123", modifier = Modifier.padding(padding))
}

@Composable
fun ContactItem() {

}