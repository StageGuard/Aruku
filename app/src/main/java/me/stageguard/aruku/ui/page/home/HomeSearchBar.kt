package me.stageguard.aruku.ui.page.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.stageguard.aruku.R
import me.stageguard.aruku.ui.common.clearFocusOnKeyboardDismiss
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringResC

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeSearchBar(
    padding: PaddingValues,
    height: Dp,
    modifier: Modifier = Modifier,
    onSearchValueChanges: (String) -> Unit
) {
    val activated = remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val textValue = remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .padding(padding)
            .fillMaxWidth()
            .height(height)
            .then(modifier)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(48.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    activated.value = !activated.value
                }) {
                    Icon(
                        imageVector = when (activated.value) {
                            true -> Icons.Outlined.ArrowBack
                            false -> Icons.Outlined.Search
                        },
                        contentDescription = "home search bar search button",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer

                    )
                }
                BasicTextField(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .clearFocusOnKeyboardDismiss(),
                    value = textValue.value,
                    onValueChange = { onSearchValueChanges(it.trim()) },
                    enabled = activated.value,
                    textStyle = MaterialTheme.typography.bodyMedium.merge(
                        TextStyle(color = MaterialTheme.colorScheme.onSurface)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { keyboardController?.hide() }),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (textValue.value.isEmpty()) {
                            Text(
                                text = R.string.home_search_conversation.stringResC,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    },
                    cursorBrush = SolidColor(value = MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Preview
@Composable
fun HomeSearchBarPreview() {
    ArukuTheme {
        HomeSearchBar(
            padding = PaddingValues(all = 16.dp),
            height = 48.dp,
        ) {
            
        }
    }
}