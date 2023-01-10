package me.stageguard.aruku.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.stageguard.aruku.R

/**
 * Created by LoliBall on 2022/4/29 9:44.
 * https://github.com/WhichWho
 */
@Composable
fun WhitePage(message: String, image: Any? = null, modifier: Modifier = Modifier) {
    Box(
        modifier = Modifier.then(modifier),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (image != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(image).build(),
                    contentDescription = message
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
            Text(
                text = message,
                color = Color.Gray.copy(alpha = 0.6f),
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center
            )
        }
    }
}