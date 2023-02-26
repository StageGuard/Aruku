package me.stageguard.aruku.ui.page.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun ChatTitleBar(
    name: String,
    avatarData: Any?,
    lastSeenTime: String = "last seen recently",
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier.size(50.dp),
            shape = CircleShape,
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            if (avatarData == null) {
                Icon(Icons.Filled.AccountCircle, null, Modifier.fillMaxSize())
            }
            AnimatedVisibility(avatarData != null, enter = fadeIn()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarData)
                        .crossfade(true)
                        .build(),
                    contentDescription = "subject avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Column(
            modifier = Modifier.padding(start = 5.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = name, style = MaterialTheme.typography.body1,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            ) // TODO
            Text(text = lastSeenTime, style = MaterialTheme.typography.subtitle1) // TODO
        }
    }
}

@Composable
fun ChatTopActions() {
    Row {
        IconButton(onClick = { }) {
            Icon(Icons.Filled.Search, "Search")
        }
        IconButton(onClick = { }) {
            Icon(Icons.Filled.MoreVert, "More")
        }
    }
}