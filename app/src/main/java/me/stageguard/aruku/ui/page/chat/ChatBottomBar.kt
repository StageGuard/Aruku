package me.stageguard.aruku.ui.page.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun RowScope.ChatBar() {
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