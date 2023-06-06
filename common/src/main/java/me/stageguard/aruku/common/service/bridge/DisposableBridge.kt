package me.stageguard.aruku.common.service.bridge

import remoter.annotations.Remoter

/**
 * handler to detach bridge from application
 */
@Remoter
interface DisposableBridge {
    fun dispose()
}

fun DisposableBridge(block: () -> Unit): DisposableBridge {
    return object : DisposableBridge {
        override fun dispose() { block() }
    }
}