package me.stageguard.aruku.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <reified T> Any?.cast(): T {
    contract { returns() implies (this@cast is T) }
    return this as T
}