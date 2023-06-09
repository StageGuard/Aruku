package me.stageguard.aruku.ui.page

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.stageguard.aruku.R
import me.stageguard.aruku.common.createAndroidLogger
import me.stageguard.aruku.service.ArukuService
import me.stageguard.aruku.ui.LocalSystemUiController
import me.stageguard.aruku.ui.theme.ArukuTheme
import java.lang.ref.WeakReference

private val logger = createAndroidLogger("ServiceConnectingPage")

@Composable
fun SplashPage() {
    SplashView()
}

@Composable
fun SplashView() {
    val systemUiController = LocalSystemUiController.current
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(isDark) {
        systemUiController.setNavigationBarColor(Color.Transparent, !isDark)
        systemUiController.setStatusBarColor(Color.Transparent, !isDark)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier
            .align(Alignment.Center)
            .size(240.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_background),
                contentDescription = "ic launcher background",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(40.dp)
                    .fillMaxSize()
                    .clip(CircleShape)
            )
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "ic launcher foreground",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    }
}

@Preview
@Composable
fun ServiceConnectingViewPreview() {
    ArukuTheme { SplashView() }
}