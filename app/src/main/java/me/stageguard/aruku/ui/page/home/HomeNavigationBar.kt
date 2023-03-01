package me.stageguard.aruku.ui.page.home

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringResC

@Composable
fun HomeNavigationBar(
    selection: HomeNavSelection,
    containerColor: Color = NavigationBarDefaults.containerColor,
    onNavigate: (HomeNavSelection, HomeNavSelection) -> Unit
) {
    NavigationBar(
        containerColor = containerColor,
        tonalElevation = 0.dp
    ) {
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
        HomeNavigationBar(homeNavSelection.value) { _, curr ->
            homeNavSelection.value = curr
        }
    }
}