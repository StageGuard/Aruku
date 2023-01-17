package me.stageguard.aruku.util

import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.ids
import net.mamoe.mirai.message.data.internalId
import kotlin.math.absoluteValue

val MessageChain.longMessageId
    get() = "${ids.sum().absoluteValue}${internalId.sum().absoluteValue}".toLong()