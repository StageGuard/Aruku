package me.stageguard.aruku.service.bridge

import me.stageguard.aruku.service.parcel.Message
import remoter.annotations.ParamOut
import remoter.annotations.Remoter

@Remoter
interface MessageSubscriber {
    fun onMessage(@ParamOut message: Message)
}