package me.stageguard.aruku.mah.dto

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.request
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import me.stageguard.aruku.common.createAndroidLogger
import me.stageguard.aruku.common.message.At
import me.stageguard.aruku.common.message.AtAll
import me.stageguard.aruku.common.message.Audio
import me.stageguard.aruku.common.message.Dice
import me.stageguard.aruku.common.message.Face
import me.stageguard.aruku.common.message.File
import me.stageguard.aruku.common.message.FlashImage
import me.stageguard.aruku.common.message.Forward
import me.stageguard.aruku.common.message.Image
import me.stageguard.aruku.common.message.MarketFace
import me.stageguard.aruku.common.message.MessageElement
import me.stageguard.aruku.common.message.PlainText
import me.stageguard.aruku.common.message.Poke
import me.stageguard.aruku.common.message.Quote
import me.stageguard.aruku.mah.ArukuMiraiApiHttpService
import me.stageguard.aruku.mah.BotSession
import net.mamoe.mirai.api.http.adapter.internal.dto.AtAllDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.AtDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.DiceDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.ElementResult
import net.mamoe.mirai.api.http.adapter.internal.dto.FaceDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.FileDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.FlashImageDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.ForwardMessageDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.ImageDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.MarketFaceDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.MessageChainDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.MessageSourceDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.PlainDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.PokeMessageDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.QuoteDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.RemoteFileDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.VoiceDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.FileInfoDTO
import org.apache.commons.codec.digest.DigestUtils
import java.io.InputStream

private val logger = createAndroidLogger("MessageElement")
private val httpClient = HttpClient(OkHttp)

fun QuoteDTO.calculateMessageId(botId: Long): Long {
    val hash = arrayOf<Any>(senderId, targetId, id)
        .foldRight(botId.hashCode()) { prop, acc -> 31 * acc + prop.hashCode() }

    return (hash.toLong() and 0x00000000ffffffff) or (id.toLong() shl 32)
}
fun MessageSourceDTO.calculateMessageId(botId: Long, senderId: Long, targetId: Long): Long {
    val hash = arrayOf<Any>(senderId, targetId, id)
        .foldRight(botId.hashCode()) { prop, acc -> 31 * acc + prop.hashCode() }

    return (hash.toLong() and 0x00000000ffffffff) or (id.toLong() shl 32)
}

context(ArukuMiraiApiHttpService)
@OptIn(InternalSerializationApi::class)
suspend fun MessageChainDTO.toMessageElements(
    session: BotSession,
    subject: Long
): List<MessageElement> = buildList {
    this@toMessageElements.filterNot { it is MessageSourceDTO }.forEach {
        when(it) {
            is AtDTO -> add(At(it.target, it.display))
            is AtAllDTO -> add(AtAll)
            is VoiceDTO -> {
                logger.i("requesting voice file ${it.url}")
                val request = httpClient.request(it.url!!).body<InputStream>()
                val size = request.available()
                val md5 = request.use { stream -> DigestUtils.md5Hex(stream) }

                add(Audio(
                    url = it.url,
                    length = it.length,
                    fileName = it.voiceId!!,
                    fileMd5 = md5,
                    extension = it.voiceId.split('.').last(),
                    fileSize = size.toLong(),
                ))
            }
            is DiceDTO -> add(Dice(it.value))
            is FaceDTO -> add(Face(it.faceId, it.name))
            is FileDTO -> {
                logger.i("querying file info ${it.id} of $subject")
                val fileInfo = session.sendAndExpect<ElementResult>(
                    "file_info",
                    FileInfoDTO(id = it.id, target = subject, group = subject, withDownloadInfo = true)
                ).data.let { e -> IncomingJson.decodeFromJsonElement(RemoteFileDTO::class.serializer(), e) }

                add(
                    File(
                        id = it.id,
                        url = fileInfo.downloadInfo?.url,
                        name = it.name,
                        md5 = fileInfo.md5?.toByteArray(),
                        extension = fileInfo.name.split('.').last(),
                        size = it.size,
                        expiryTime = fileInfo.uploadTime?.plus(7 * 24 * 60 * 60) // plus 7 days
                    )
                )
            }
            is FlashImageDTO ->
                add(FlashImage(
                    url = it.url ?: "",
                    uuid = it.imageId!!,
                    width = it.width,
                    height = it.height
                ))
            is ForwardMessageDTO -> {
                add(Forward(it.nodeList.map { node ->
                    Forward.Node(
                        senderId = node.senderId ?: 0L,
                        senderName = node.senderName ?: "",
                        time = node.time?.toLong() ?: 0L,
                        messages = node.messageChain?.toMessageElements(session, subject) ?: listOf()
                    )
                }))
            }
            is ImageDTO ->
                add(Image(
                    url = it.url ?: "",
                    uuid = it.imageId!!,
                    width = it.width,
                    height = it.height,
                    isEmoticons = it.isEmoji
                ))
            is MarketFaceDTO -> add(MarketFace(it.id, it.name))
            is PlainDTO -> add(PlainText(it.text))
            is PokeMessageDTO -> add(Poke(it.name, 0, 0))
            is QuoteDTO -> add(Quote(it.calculateMessageId(session.accountNo)))

            else -> {
                logger.i("Unsupported message element: $it")
                add(PlainText(it.toString()))
            }
        }
    }
}