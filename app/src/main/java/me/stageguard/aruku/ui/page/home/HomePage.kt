package me.stageguard.aruku.ui.page.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.stageguard.aruku.R
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringRes

@Composable
fun HomePage(
    botList: State<List<Long>>,
    navigateToLoginPage: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel { HomeViewModel() }
    HomeView(botList, navigateToLoginPage)
}

@Composable
fun HomeView(
    botList: State<List<Long>>,
    navigateToLoginPage: () -> Unit
) {
    val botListExpanded = remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        Text(
            text = R.string.messages.stringRes,
            style = TextStyle(fontSize = 35.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 15.dp, top = 35.dp)
        )
        IconButton(onClick = {
            if (botList.value.isNotEmpty()) {
                botListExpanded.value = !botListExpanded.value
            } else {
                navigateToLoginPage()
            }
        }, modifier = Modifier.align(Alignment.BottomEnd).padding(0.dp)) {
            Image(Icons.Outlined.AccountCircle, "Account")
        }
    }
}

@Preview
@Composable
fun MessageViewPreview() {
    val list = remember { mutableStateOf(listOf<Long>()) }
    ArukuTheme { HomeView(list) {} }
}