package me.stageguard.aruku.mah.dto

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.serializer
import net.mamoe.mirai.api.http.adapter.internal.dto.BotEventDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.DTO
import net.mamoe.mirai.api.http.adapter.internal.dto.EventDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.MessagePacketDTO
import kotlin.reflect.KClass

@Serializable
data class WsOutgoing(
    val syncId: Int,
    val command: String,
    val subCommand: String? = null,
    val content: @Serializable DTO? = null
)

@Serializable
data class WsIncoming(
    val syncId: String,
    val data: JsonElement
)

@OptIn(InternalSerializationApi::class)
val IncomingJson = Json {
    encodeDefaults = true
    isLenient = true
    ignoreUnknownKeys = true

    serializersModule = SerializersModule {
        polymorphicSealedClass(EventDTO::class, MessagePacketDTO::class)
        polymorphicSealedClass(EventDTO::class, BotEventDTO::class)
    }
}

val json = Json {
    encodeDefaults = true
    isLenient = true
    ignoreUnknownKeys = true
}

@InternalSerializationApi
@Suppress("UNCHECKED_CAST")
private fun <B : Any, S : B> SerializersModuleBuilder.polymorphicSealedClass(
    baseClass: KClass<B>,
    sealedClass: KClass<S>
) {
    sealedClass.sealedSubclasses.forEach {
        val c = it as KClass<S>
        polymorphic(baseClass, c, c.serializer())
    }
}