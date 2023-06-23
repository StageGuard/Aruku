package me.stageguard.aruku.mah

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.headers
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import me.stageguard.aruku.common.createAndroidLogger
import me.stageguard.aruku.mah.dto.IncomingJson
import me.stageguard.aruku.mah.dto.WsIncoming
import me.stageguard.aruku.mah.dto.WsOutgoing
import me.stageguard.aruku.mah.dto.json
import net.mamoe.mirai.api.http.adapter.internal.dto.DTO
import net.mamoe.mirai.api.http.adapter.internal.dto.EventDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.VerifyRetDTO

class BotSession(
    coroutineScope: CoroutineScope,
    client: HttpClient,
    val accountNo: Long,
): CoroutineScope by coroutineScope {
    private val logger = createAndroidLogger()

    private val verifyResult = CompletableDeferred<Int>()

    private val outgoingChannel = Channel<WsOutgoing>()
    private val eventFlow = MutableSharedFlow<EventDTO>(
        extraBufferCapacity = 20, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val commandChannel = Channel<WsIncoming>(capacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST) {
        logger.w("command result $it is not received and processed.")
    }

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    private val webSocketHolder: Job = launch(start = CoroutineStart.LAZY) {
        logger.i("starting websocket session of bot $accountNo")
        client.webSocket("ws://$HOST/all", request = {
            headers {
                append("verifyKey", AUTH_KEY)
                append("qq", accountNo.toString())
            }
        }) {
            val sender = launch {
                for (outgoing in outgoingChannel) {
                    logger.d("websocket outgoing: $outgoing")
                    send(Frame.Text(json.encodeToString(WsOutgoing.serializer(), outgoing)))
                }
            }
            val receiver = launch recv@ {
                for (data in incoming) {
                    if (data !is Frame.Text) continue

                    val deserialized = data.data.inputStream().use {
                        json.decodeFromStream(WsIncoming.serializer(), it)
                    }

                    // verify result
                    if (deserialized.syncId.isEmpty()) {
                        val retCode = deserialized.data.jsonObject["code"]!!.jsonPrimitive.content.toInt()
                        if (retCode != 0) {
                            verifyResult.complete(retCode)
                            this@BotSession.close()
                            return@recv
                        }
                        val verifyRet = json.decodeFromJsonElement(
                            VerifyRetDTO::class.serializer(), deserialized.data
                        )
                        verifyResult.complete(verifyRet.code)
                        logger.i("session of bot $accountNo is started.")
                        continue
                    }

                    // event
                    if (deserialized.syncId.toIntOrNull() == RESERVED_SYNC_ID) {
                        val event = IncomingJson.decodeFromJsonElement(
                            EventDTO::class.serializer(),
                            deserialized.data
                        )
                        eventFlow.emit(event)
                        logger.d("websocket incoming event: $event.")
                        continue
                    }

                    // command result
                    commandChannel.send(deserialized)
                    logger.d("websocket incoming command: $deserialized.")
                }
            }

            sender.join()
            close()
            receiver.join()
        }
    }

    val connected get() = !verifyResult.isActive
    val active get() = webSocketHolder.isActive

    fun close() {
        webSocketHolder.cancel()
    }

    fun start() {
        webSocketHolder.start()
    }

    suspend fun awaitConnection() = withTimeout(10000L) {
        verifyResult.await()
    }

    suspend fun send(
        command: String,
        subCommand: String?,
        syncId: Int,
        data: DTO?
    ) {
        outgoingChannel.send(WsOutgoing(syncId, command, subCommand, data))
    }

    suspend fun commandFlow() = commandChannel.receiveAsFlow()

    suspend fun eventFlow(): Flow<EventDTO> {
        return eventFlow
    }

}