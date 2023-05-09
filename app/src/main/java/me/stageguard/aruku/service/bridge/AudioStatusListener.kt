package me.stageguard.aruku.service.bridge

import me.stageguard.aruku.cache.AudioCache
import remoter.annotations.ParamOut
import remoter.annotations.Remoter

@Remoter
interface AudioStatusListener {
    fun onState(@ParamOut state: AudioCache.State)
}

fun AudioStatusListener(block: (state: AudioCache.State) -> Unit): AudioStatusListener {
    return object : AudioStatusListener {
        override fun onState(state: AudioCache.State) {
            block(state)
        }
    }
}