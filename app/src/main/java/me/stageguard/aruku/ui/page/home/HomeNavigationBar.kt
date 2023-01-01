package me.stageguard.aruku.ui.page.home

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
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