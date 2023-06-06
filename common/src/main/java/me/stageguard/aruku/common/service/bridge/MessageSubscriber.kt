package me.stageguard.aruku.common.service.bridge

import me.stageguard.aruku.common.service.parcel.Message
import remoter.annotations.ParamOut
import remoter.annotations.Remoter

@Remoter
interface MessageSubscriber {
    fun onMessage(@ParamOut message: Message)
}