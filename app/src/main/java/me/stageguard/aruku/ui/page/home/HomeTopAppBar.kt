package me.stageguard.aruku.ui.page.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.stageguard.aruku.ui.theme.ArukuTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(
    title: String,
    account: BasicAccountInfo?,
    barColors: TopAppBarColors,
    modifier: Modifier = Modifier,
    accountStateColor: Color,
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
    onAvatarClick: () -> Unit,
) {
//    val avatarProgressIndicator by remember { derivedStateOf { state.value is AccountState.Login } }
//    val activeAccountOnline by remember { derivedStateOf { state.value is AccountState.Online } }
//    val context = LocalContext.current
    CenterAlignedTopAppBar(
        modifier = modifier,
        colors = barColors,
        scrollBehavior = scrollBehavior,
        title = { Text(text = title) },
        actions = {
            IconButton(
                onClick = onAvatarClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(48.dp)
                    .padding(4.dp)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithCache {
                        val path = Path()
                        path.addOval(
                            Rect(
                                topLeft = Offset.Zero,
                                bottomRight = Offset(size.width, size.height)
                            )
                        )
                        onDrawWithContent {
                            clipPath(path) {
                                this@onDrawWithContent.drawContent()
                            }
                            val dotSize = 18f
                            drawCircle(
                                Color.Black,
                                radius = dotSize,
                                center = Offset(
                                    x = size.width - dotSize - 6f,
                                    y = size.height - dotSize - 6f
                                ),
                                blendMode = BlendMode.Clear
                            )
                            drawCircle(
                                accountStateColor, radius = dotSize * 0.8f,
                                center = Offset(
                                    x = size.width - dotSize - 6f,
                                    y = size.height - dotSize - 6f
                                )
                            )
                        }

                    }
            ) {
                Box(Modifier.size(45.dp), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(account?.avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "account avatar url",
                        modifier = Modifier
                            .size(45.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun HomeTopAppBarPreview() {
    ArukuTheme {
        HomeTopAppBar(
//            activeAccountOnline = true,
            title = "123title",
            barColors = TopAppBarDefaults.topAppBarColors(),
            account = BasicAccountInfo(1234567890, "StageGuard", null),
            accountStateColor = Color.Gray
        ) {}
    }
}