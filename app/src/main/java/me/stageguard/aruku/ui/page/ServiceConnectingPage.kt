package me.stageguard.aruku.ui.page

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.stageguard.aruku.service.ArukuMiraiService
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.toLogTag
import java.lang.ref.WeakReference

private val TAG = Unit.toLogTag("ServiceConnectingPage")

@Composable
fun ServiceConnectingPage(connectorRef: WeakReference<ServiceConnection>) {
    val context = LocalContext.current
    SideEffect {
        val startResult = context.startService(Intent(context, ArukuMiraiService::class.java))
        if (startResult == null) Log.e(TAG, "Cannot start ArukuMiraiService.")
        val bindResult = connectorRef.get()?.let {
            context.bindService(Intent(context, ArukuMiraiService::class.java), it, Context.BIND_ABOVE_CLIENT)
        }
        if (bindResult != true) Log.e(TAG, "Cannot bind ArukuMiraiService.")
    }
    ServiceConnectingView()
}

@Composable
fun ServiceConnectingView() {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.wrapContentSize().align(Alignment.Center)) {
            Text("Connecting to ArukuMiraiService",
                style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(start = 30.dp, end = 30.dp, bottom = 10.dp)
            )
            Text("We lost connection to ArukuMiraiService, please wait until it is connected.",
                style = TextStyle(fontSize = 18.sp),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 30.dp, vertical = 10.dp)
            )
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(top = 50.dp)
                    .size(60.dp)
                    .align(Alignment.CenterHorizontally),
                strokeWidth = 6.dp
            )
        }
    }
}

@Preview
@Composable
fun ServiceConnectingViewPreview() {
    ArukuTheme { ServiceConnectingView() }
}