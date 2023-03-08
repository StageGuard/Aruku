package me.stageguard.aruku.service.bridge

import me.stageguard.aruku.service.parcel.ArukuRoamingMessage
import remoter.annotations.Remoter

@Remoter
interface RoamingQueryBridge {
    fun getMessagesBefore(seq: Int, count: Int, includeSeq: Boolean): List<ArukuRoamingMessage>?
    fun getLastMessageSeq(): Int?
}