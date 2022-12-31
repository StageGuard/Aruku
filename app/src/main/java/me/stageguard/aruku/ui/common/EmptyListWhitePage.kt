package me.stageguard.aruku.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import me.stageguard.aruku.R
import me.stageguard.aruku.util.stringResC

/**
 * Created by LoliBall on 2022/12/18 22:43.
 * https://github.com/WhichWho
 */
@Composable
fun <T : Any> EmptyListWhitePage(data: LazyPagingItems<T>?, onErrorClick: () -> Unit) {
    if (data == null || data.itemCount == 0) {
        when (data?.loadState?.refresh) {
            null -> {
                WhitePage(
                    message = R.string.list_empty.stringResC,
                    image = R.mipmap.load_empty,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is LoadState.NotLoading -> {
                WhitePage(
                    message = R.string.list_empty.stringResC,
                    image = R.mipmap.load_empty,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is LoadState.Loading -> {
                WhitePage(
                    message = R.string.list_loading.stringResC,
                    image = R.mipmap.load_loading,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is LoadState.Error -> {
                WhitePage(
                    message = R.string.list_failed.stringResC,
                    image = R.mipmap.load_error,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onErrorClick)
                )
            }
        }
    }
}
