package me.stageguard.aruku.domain.data.message

import android.os.Parcelable
import android.util.Log
import me.stageguard.aruku.domain.data.message.*
import me.stageguard.aruku.util.tag
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.file.AbsoluteFileFolder.Companion.extension
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.OnlineAudio
import net.mamoe.mirai.utils.MiraiExperimentalApi

sealed interface MessageElement : Parcelable {
    fun contentToString(): String
}

fun MessageSource.calculateMessageId(): Int {
    var result = botId.hashCode()
    result = 31 * result + time.hashCode()
    result = 31 * result + fromId.hashCode()
    result = 31 * result + targetId.hashCode()
    ids.forEach { id -> result = 31 * result + id.hashCode() }
    internalIds.forEach { id -> result = 31 * result + id.hashCode() }
    return result
}

@OptIn(MiraiExperimentalApi::class)
suspend fun MessageChain.toMessageElements(event: MessageEvent): List<MessageElement> = buildList {
    this@toMessageElements.forEach { m ->
        when (m) {
            is net.mamoe.mirai.message.data.At -> {
                add(
                    At(
                        m.target,
                        m.getDisplay(if (event is GroupMessageEvent) event.group else null)
                    )
                )
            }

            is net.mamoe.mirai.message.data.AtAll -> AtAll
            is net.mamoe.mirai.message.data.Audio -> {
                m as OnlineAudio
                add(
                    Audio(
                        m.urlForDownload,
                        m.length,
                        m.filename,
                        m.fileMd5,
                        m.fileSize,
                        m.codec.formatName
                    )
                )
            }

            is net.mamoe.mirai.message.data.Dice -> Dice(m.value)
            is net.mamoe.mirai.message.data.Face -> Face(m.id, m.name)
            is net.mamoe.mirai.message.data.FileMessage -> {
                val subject = event.subject
                if (subject !is Group) {
                    Log.i(
                        tag("MessageElement"),
                        "Received file message while sender is not group, which is not supported."
                    )
                }
                val file = if (subject is Group) m.toAbsoluteFile(subject) else null
                add(
                    File(
                        file?.getUrl(),
                        m.name,
                        file?.md5,
                        file?.extension,
                        m.size,
                        file?.expiryTime
                    )
                )
            }

            is net.mamoe.mirai.message.data.FlashImage -> {
                add(FlashImage(m.image.queryUrl(), m.image.imageId, m.image.width, m.image.height))
            }

            is net.mamoe.mirai.message.data.ForwardMessage -> {
                add(Forward(m.nodeList.map {
                    Forward.Node(
                        it.senderId,
                        it.senderName,
                        it.time.toLong(),
                        it.messageChain.toMessageElements(event)
                    )
                }))
            }

            is net.mamoe.mirai.message.data.Image -> {
                add(Image(m.queryUrl(), m.imageId, m.width, m.height))
            }

            is net.mamoe.mirai.message.data.MarketFace -> add(MarketFace(m.id, m.name))
            is net.mamoe.mirai.message.data.PlainText -> add(PlainText(m.content))
            is net.mamoe.mirai.message.data.PokeMessage -> add(Poke(m.name, m.pokeType, m.id))
            is net.mamoe.mirai.message.data.QuoteReply -> add(Quote(m.source.calculateMessageId()))
            is net.mamoe.mirai.message.data.RockPaperScissors -> {
                add(RPS.values[m.id] ?: RPS(m.id, m.name))
            }

            is net.mamoe.mirai.message.data.VipFace -> add(VipFace(m.kind.id, m.kind.name, m.count))

            else -> {
                Log.i(tag("MessageElement"), "Unsupported message element: $m")
                add(PlainText(m.contentToString()))
            }
        }
    }
}