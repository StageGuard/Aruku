package me.stageguard.aruku.service.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.stageguard.aruku.service.parcel.Message
import remoter.annotations.Remoter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Remoter
interface RoamingQueryBridge {
    fun getLastMessageId(): Long?

    fun getMessagesBefore(messageId: Long, count: Int, exclude: Boolean): List<Message>?


}

suspend fun <R> RoamingQueryBridge.suspendIO(block: RoamingQueryBridge.() -> R): R {
    return withContext(Dispatchers.IO) {
        suspendCoroutine { continuation -> continuation.resume(block()) }
    }
}