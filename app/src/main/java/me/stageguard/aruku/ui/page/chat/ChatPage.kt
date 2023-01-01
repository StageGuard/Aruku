package me.stageguard.aruku.ui.page.chat

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.stageguard.aruku.ui.common.ArrowBack

/**
 * Created by LoliBall on 2023/1/1 19:30.
 * https://github.com/WhichWho
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPage(target: Int) {

    Scaffold(
        modifier = Modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        topBar = {
            TopAppBar(
                navigationIcon = { ArrowBack() },
                title = { TitleBar(target) },
                actions = { TopActions() }
            )
        },
        bottomBar = {
            BottomAppBar(Modifier.height(60.dp)) {
                ChatBar()
            }
        }
    ) {
        // TODO chat background
        // TODO chat message list
    }

}

@Composable
private fun TitleBar(target: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
//        AsyncImage(
//            model = ImageRequest.Builder(LocalContext.current)
//                .data(null)
//                .crossfade(true)
//                .build(),
//            contentDescription = "icon"
//        )
        Icon(Icons.Filled.AccountBox, null, Modifier.size(50.dp))
        Column(
            modifier = Modifier.padding(start = 5.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = target.toString(), style = MaterialTheme.typography.body1) // TODO
            Text(text = "last seen time", style = MaterialTheme.typography.subtitle1) // TODO
        }
    }
}

@Composable
private fun TopActions() {
    Row {
        IconButton(onClick = { }) {
            Icon(Icons.Filled.Search, "Search")
        }
        IconButton(onClick = { }) {
            Icon(Icons.Filled.MoreVert, "More")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.ChatBar() {
    IconButton(onClick = { }) {
        Icon(Icons.Filled.SentimentSatisfied, "Emoji")
    }
    var text by remember { mutableStateOf("") }
    BasicTextField(
        modifier = Modifier.weight(1f),
        value = text,
        textStyle = MaterialTheme.typography.body1,
        singleLine = true,
        decorationBox = @Composable { innerTextField ->
            Box {
                if (text.isEmpty()) {
                    Text("Placeholder")
                }
                innerTextField()
            }
        },
        onValueChange = { text = it }
    )
    if (text.isEmpty()) {
        IconButton(onClick = { }) {
            Icon(Icons.Filled.AttachFile, "Attach")
        }
        IconButton(onClick = { }) {
            Icon(Icons.Filled.Mic, "Mic")
        }
    } else {
        IconButton(onClick = { }) {
            Icon(Icons.Filled.Send, "Send", tint = MaterialTheme.colors.primaryVariant)
        }
    }
}