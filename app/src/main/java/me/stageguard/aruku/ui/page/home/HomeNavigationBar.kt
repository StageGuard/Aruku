package me.stageguard.aruku.ui.page.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import me.stageguard.aruku.R
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringResC

@Composable
fun HomeNavigationBar(
    selection: HomeNavSelection,
    onNavigate: (HomeNavSelection, HomeNavSelection) -> Unit
) {
    NavigationBar {
        homeNaves.forEach { (k, v) ->
            NavigationBarItem(
                selected = selection == k,
                onClick = { onNavigate(selection, k) },
                icon = { Icon(v.icon, v.label.stringResC) },
                label = { Text(v.label.stringResC, style = MaterialTheme.typography.labelSmall) },
                alwaysShowLabel = false
            )
        }
    }
}

val homeNaves = mapOf(
    HomeNavSelection.MESSAGE to HomeNav(
        selection = HomeNavSelection.MESSAGE,
        icon = Icons.Default.Message,
        label = R.string.home_nav_message,
        content = { HomeNavMessage(it) }
    ),
    HomeNavSelection.CONTACT to HomeNav(
        selection = HomeNavSelection.CONTACT,
        icon = Icons.Default.Contacts,
        label = R.string.home_nav_contact,
        content = { Text("!23123", modifier = Modifier.padding(it)) }
    ),
    HomeNavSelection.PROFILE to HomeNav(
        selection = HomeNavSelection.PROFILE,
        icon = Icons.Default.AccountCircle,
        label = R.string.home_nav_profile,
        content = { Text("46456", modifier = Modifier.padding(it)) }
    )
)

data class HomeNav(
    val selection: HomeNavSelection,
    val icon: ImageVector,
    @StringRes val label: Int,
    val content: @Composable (PaddingValues) -> Unit,
    val topBar: (@Composable () -> Unit)? = null,
)

@Preview
@Composable
fun HomeNavigationBarPreview() {
    val homeNavSelection = remember { mutableStateOf(HomeNavSelection.MESSAGE) }
    ArukuTheme {
        HomeNavigationBar(homeNavSelection.value) { prev, cuur ->
            homeNavSelection.value = cuur
        }
    }
}