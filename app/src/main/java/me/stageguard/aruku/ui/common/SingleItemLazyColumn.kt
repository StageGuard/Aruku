package me.stageguard.aruku.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable

@Composable
fun SingleItemLazyColumn(block: @Composable ColumnScope.() -> Unit) {
    LazyColumn {
        item {
            Column {
                block(this)
            }
        }
    }
}