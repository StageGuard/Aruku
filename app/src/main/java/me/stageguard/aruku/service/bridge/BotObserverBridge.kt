package me.stageguard.aruku.service.bridge

import remoter.annotations.ParamOut
import remoter.annotations.Remoter

@Remoter
interface BotObserverBridge {
    fun onChange(@ParamOut bots: List<Long>)
}