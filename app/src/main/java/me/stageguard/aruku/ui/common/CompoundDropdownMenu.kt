package me.stageguard.aruku.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Created by LoliBall on 2022/9/6 18:34.
 * https://github.com/WhichWho
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> CompoundDropdownMenu(
    list: List<T>, current: T,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean,
    onClickItem: (T) -> Unit
) {
    val expanded = remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            current.toString(), onValueChange = {}, readOnly = true,
            label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded.value = !expanded.value },
            shape = RoundedCornerShape(15.dp),
            trailingIcon = {
                IconButton(onClick = { expanded.value = !expanded.value }) {
                    Image(
                        Icons.Outlined.ExpandMore,
                        contentDescription = "Expand $label dropdown menu."
                    )
                }
            },
            enabled = enabled
        )
        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }
        ) {
            list.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.toString()) },
                    onClick = { onClickItem(p); expanded.value = false }
                )
            }
        }
    }
}
