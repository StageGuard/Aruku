package me.stageguard.aruku.service.bridge

import me.stageguard.aruku.cache.AudioCache
import remoter.annotations.Remoter

@Remoter
interface AudioStateListener {
    fun onState(state: AudioCache.State)
}