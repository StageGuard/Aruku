package me.stageguard.aruku.service.bridge

import me.stageguard.aruku.service.parcel.BackendState
import remoter.annotations.Remoter

@Remoter
interface BackendStateListener {
    fun onState(state: BackendState)
}

fun BackendStateListener(block: (BackendState) -> Unit): BackendStateListener {
    return object : BackendStateListener {
        override fun onState(state: BackendState) {
            block(state)
        }
    }
}