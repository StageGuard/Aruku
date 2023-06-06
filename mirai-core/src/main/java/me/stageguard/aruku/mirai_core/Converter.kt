package me.stageguard.aruku.mirai_core

import me.stageguard.aruku.common.createAndroidLogger
import me.stageguard.aruku.common.message.At
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
import me.stageguard.aruku.common.message.RPS
import me.stageguard.aruku.common.message.VipFace
import me.stageguard.aruku.common.service.parcel.ContactId
import me.stageguard.aruku.common.service.parcel.ContactInfo
import me.stageguard.aruku.common.service.parcel.ContactType
import me.stageguard.aruku.common.service.parcel.GroupMemberInfo
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Friend
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.contact.file.AbsoluteFileFolder.Companion.extension
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.contact.remarkOrNameCardOrNick
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.OnlineAudio
import net.mamoe.mirai.utils.MiraiExperimentalApi
import net.mamoe.mirai.utils.getRandomString
import net.mamoe.mirai.utils.toUHexString

private val logger = createAndroidLogger("MessageElement")
fun Member.toGroupMemberInfo(): GroupMemberInfo {
    return GroupMemberInfo(
        senderId = id,
        senderName = remarkOrNameCardOrNick,
        senderAvatarUrl = avatarUrl
    )
}

fun Group.toContactInfo() = ContactInfo(
    id = ContactId(
        type = ContactType.GROUP,
        subject = this.id
    ),
    name = this.name,
    avatarUrl = this.avatarUrl
)

fun Friend.toContactInfo() = ContactInfo(
    id = ContactId(
        type = ContactType.FRIEND,
        subject = this.id
    ),
    name = this.nameCardOrNick,
    avatarUrl = this.avatarUrl
)

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

                is net.mamoe.mirai.message.data.AtAll -> add(me.stageguard.aruku.common.message.AtAll)
                is net.mamoe.mirai.message.data.Audio -> {
                    m as OnlineAudio
                    add(
                        Audio(
                            m.urlForDownload,
                            m.length,
                            m.filename,
                            m.fileMd5.toUHexString(""),
                            m.fileSize,
                            m.codec.formatName,
                            m.urlForDownload
                        )
                    )
                }

                is net.mamoe.mirai.message.data.Dice -> add(Dice(m.value))
                is net.mamoe.mirai.message.data.Face -> add(Face(m.id, m.name))
                is net.mamoe.mirai.message.data.FileMessage -> {
                    if (contact !is Group) {
                        logger.i("Received file message while sender is not group, which is not supported.")
                    }
                    val file = if (contact is Group) m.toAbsoluteFile(contact) else null
                    add(
                        File(
                            file?.id,
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
                    logger.i("Unsupported message element: $m")
                    add(PlainText(m.contentToString()))
                }
            }
        }
    }