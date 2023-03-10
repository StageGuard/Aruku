package me.stageguard.aruku.service.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.stageguard.aruku.service.parcel.ArukuRoamingMessage
import remoter.annotations.Remoter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Remoter
interface RoamingQueryBridge {
    fun getMessagesBefore(seq: Int, count: Int, includeSeq: Boolean): List<ArukuRoamingMessage>?
    fun getLastMessageSeq(): Int?
}

suspend fun <R> RoamingQueryBridge.suspendIO(block: RoamingQueryBridge.() -> R): R {
    return withContext(Dispatchers.IO) {
        suspendCoroutine { continuation -> continuation.resume(block()) }
    }
}