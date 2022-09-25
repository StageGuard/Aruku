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
        homeNavs.forEach { (k, v) ->
            NavigationBarItem(
                selected = selection == k,
                onClick = {
                    onNavigate(selection, k)
                },
                icon = { Icon(v.icon, v.label.stringResC) },
                label = { Text(v.label.stringResC, style = MaterialTheme.typography.labelSmall) },
                alwaysShowLabel = false
            )
        }
    }
}

val homeNavs = mapOf(
    HomeNavSelection.MESSAGE to HomeNav(
        HomeNavSelection.MESSAGE,
        Icons.Default.Message,
        R.string.home_nav_message
    ) {
        HomeNavMessage(it)
    },
    HomeNavSelection.CONTACT to HomeNav(
        HomeNavSelection.CONTACT,
        Icons.Default.Contacts,
        R.string.home_nav_contact
    ) {
        Text("!23123", modifier = Modifier.padding(it))
    },
    HomeNavSelection.PROFILE to HomeNav(
        HomeNavSelection.PROFILE,
        Icons.Default.AccountCircle,
        R.string.home_nav_profile
    ) {
        Text("46456", modifier = Modifier.padding(it))
    },
)

data class HomeNav(
    val selection: HomeNavSelection,
    val icon: ImageVector,
    @StringRes val label: Int,
    val composable: @Composable (PaddingValues) -> Unit
)

@Preview
@Composable
fun HomeNavigationBarPreview() {
    val homeNavSelection = remember { mutableStateOf(HomeNavSelection.MESSAGE) }
    ArukuTheme {
        HomeNavigationBar(homeNavSelection.value) { prev, cuur ->

        }
    }
}