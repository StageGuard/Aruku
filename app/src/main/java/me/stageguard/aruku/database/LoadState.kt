package me.stageguard.aruku.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed interface LoadState<T> {
    class Loading<T> : LoadState<T>
    data class Ok<T>(val data: T) : LoadState<T>
    data class Error<T>(val throwable: Throwable) : LoadState<T>
}

fun <T, R> Flow<LoadState<T>>.mapOk(transform: suspend (T) -> R): Flow<LoadState<R>> =
    map {
        when (it) {
            is LoadState.Loading<T> -> LoadState.Loading()
            is LoadState.Ok<T> -> LoadState.Ok(transform(it.data))
            is LoadState.Error -> LoadState.Error(it.throwable)
        }
    }

@OptIn(ExperimentalContracts::class)
fun <T> LoadState<T>.ok(): LoadState.Ok<T> {
    contract {
        returns() implies (this@ok is LoadState.Ok<T>)
    }
    return this as LoadState.Ok<T>
}