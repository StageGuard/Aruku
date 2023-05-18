package me.stageguard.aruku.domain.data.message

import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize
import me.stageguard.aruku.util.tag
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.file.AbsoluteFileFolder.Companion.extension
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.OnlineAudio
import net.mamoe.mirai.utils.MiraiExperimentalApi
import net.mamoe.mirai.utils.toUHexString

@Parcelize
sealed interface MessageElement : Parcelable {
    fun contentToString(): String

    fun isText() = this is PlainText || this is At || this is AtAll
    fun isImage() = this is Image || this is FlashImage || this is Face
}

fun MessageSource.calculateMessageId(): Long {
    val hash = arrayOf<Any>(time, fromId, targetId, *ids.toTypedArray())
        .foldRight(botId.hashCode()) { prop, acc -> 31 * acc + prop.hashCode() }

    return (hash.toLong() and 0x00000000ffffffff) or (ids.first().toLong() shl 32)
}

fun getSeqByMessageId(messageId: Long): Int = (messageId shr 32).toInt()

@OptIn(MiraiExperimentalApi::class)
suspend fun MessageChain.toMessageElements(contact: Contact? = null): List<MessageElement> =
    buildList {
        this@toMessageElements.filterNot { it is MessageSource }.forEach { m ->
            when (m) {
                is net.mamoe.mirai.message.data.At -> {
                    add(At(m.target, m.getDisplay(contact as? Group)))
                }

                is net.mamoe.mirai.message.data.AtAll -> AtAll
                is net.mamoe.mirai.message.data.Audio -> {
                    m as OnlineAudio
                    add(
                        Audio(
                            m.urlForDownload,
                            m.length,
                            m.filename,
                            m.fileMd5.toUHexString(""),
                            m.fileSize,
                            m.codec.formatName
                        )
                    )
                }

                is net.mamoe.mirai.message.data.Dice -> Dice(m.value)
                is net.mamoe.mirai.message.data.Face -> Face(m.id, m.name)
                is net.mamoe.mirai.message.data.FileMessage -> {
                    if (contact !is Group) {
                        Log.i(
                            tag("MessageElement"),
                            "Received file message while sender is not group, which is not supported."
                        )
                    }
                    val file = if (contact is Group) m.toAbsoluteFile(contact) else null
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
                    add(
                        FlashImage(
                            m.image.queryUrl(),
                            m.image.imageId,
                            m.image.width,
                            m.image.height
                        )
                    )
                }

                is net.mamoe.mirai.message.data.ForwardMessage -> {
                    add(Forward(m.nodeList.map {
                        Forward.Node(
                            it.senderId,
                            it.senderName,
                            it.time.toLong(),
                            it.messageChain.toMessageElements(contact)
                        )
                    }))
                }

                is net.mamoe.mirai.message.data.Image -> {
                    add(Image(m.queryUrl(), m.imageId, m.width, m.height, m.isEmoji))
                }

                is net.mamoe.mirai.message.data.MarketFace -> add(MarketFace(m.id, m.name))
                is net.mamoe.mirai.message.data.PlainText -> add(PlainText(m.content))
                is net.mamoe.mirai.message.data.PokeMessage -> add(Poke(m.name, m.pokeType, m.id))
                is net.mamoe.mirai.message.data.QuoteReply -> add(Quote(m.source.calculateMessageId()))
                is net.mamoe.mirai.message.data.RockPaperScissors -> {
                    add(RPS.values[m.id] ?: RPS(m.id, m.name))
                }

                is net.mamoe.mirai.message.data.VipFace -> add(
                    VipFace(
                        m.kind.id,
                        m.kind.name,
                        m.count
                    )
                )

                else -> {
                    Log.i(tag("MessageElement"), "Unsupported message element: $m")
                    add(PlainText(m.contentToString()))
                }
            }
        }
    }

@Suppress("NOTHING_TO_INLINE")
inline fun Collection<MessageElement>.contentToString() =
    joinToString("") { it.contentToString() }