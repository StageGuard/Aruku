/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.api.http.adapter.internal.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class MessagePacketDTO : EventDTO() {
    lateinit var messageChain: MessageChainDTO
}

typealias MessageChainDTO = List<MessageDTO>

@Serializable
@SerialName("FriendMessage")
data class FriendMessagePacketDTO(val sender: QQDTO) : MessagePacketDTO()

@Serializable
@SerialName("FriendSyncMessage")
data class FriendSyncMessagePacketDTO(val subject: QQDTO) : MessagePacketDTO()

@Serializable
@SerialName("GroupMessage")
data class GroupMessagePacketDTO(val sender: MemberDTO) : MessagePacketDTO()

@Serializable
@SerialName("GroupSyncMessage")
data class GroupSyncMessagePacketDTO(val subject: GroupDTO) : MessagePacketDTO()

@Serializable
@SerialName("TempMessage")
data class TempMessagePacketDTO(val sender: MemberDTO) : MessagePacketDTO()

@Serializable
@SerialName("TempSyncMessage")
data class TempSyncMessagePacketDTO(val subject: MemberDTO) : MessagePacketDTO()

@Serializable
@SerialName("StrangerMessage")
data class StrangerMessagePacketDTO(val sender: QQDTO) : MessagePacketDTO()

@Serializable
@SerialName("StrangerSyncMessage")
data class StrangerSyncMessagePacketDTO(val subject: QQDTO) : MessagePacketDTO()

@Serializable
@SerialName("OtherClientMessage")
data class OtherClientMessagePacketDTO(val sender: OtherClientDTO) : MessagePacketDTO()

// Message
@Serializable
@SerialName("Source")
data class MessageSourceDTO(val id: Int, val time: Int) : MessageDTO()

@Serializable
@SerialName("At")
data class AtDTO(val target: Long, val display: String = "") : MessageDTO()

@Serializable
@SerialName("AtAll")
data class AtAllDTO(val target: Long = 0) : MessageDTO() // target为保留字段

@Serializable
@SerialName("Face")
data class FaceDTO(val faceId: Int = -1, val name: String = "") : MessageDTO()

@Serializable
@SerialName("Plain")
data class PlainDTO(val text: String) : MessageDTO()

interface ImageLikeDTO {
    val imageId: String?
    val url: String?
    val path: String?
    val base64: String?
    val width: Int
    val height: Int
    val size: Long
    val imageType: String
    val isEmoji: Boolean
}

interface VoiceLikeDTO {
    val voiceId: String?
    val url: String?
    val path: String?
    val base64: String?
    val length: Long
}

@Serializable
@SerialName("Image")
data class ImageDTO(
    override val imageId: String? = null,
    override val url: String? = null,
    override val path: String? = null,
    override val base64: String? = null,
    override val width: Int = 0,
    override val height: Int = 0,
    override val size: Long = 0,
    override val imageType: String = "UNKNOWN",
    override val isEmoji: Boolean = false,
) : MessageDTO(), ImageLikeDTO

@Serializable
@SerialName("FlashImage")
data class FlashImageDTO(
    override val imageId: String? = null,
    override val url: String? = null,
    override val path: String? = null,
    override val base64: String? = null,
    override val width: Int = 0,
    override val height: Int = 0,
    override val size: Long = 0,
    override val imageType: String = "UNKNOWN",
    override val isEmoji: Boolean = false,
) : MessageDTO(), ImageLikeDTO

@Serializable
@SerialName("Voice")
data class VoiceDTO(
    override val voiceId: String? = null,
    override val url: String? = null,
    override val path: String? = null,
    override val base64: String? = null,
    override val length: Long = 0L,
) : MessageDTO(), VoiceLikeDTO

@Serializable
@SerialName("Xml")
data class XmlDTO(val xml: String) : MessageDTO()

@Serializable
@SerialName("Json")
data class JsonDTO(val json: String) : MessageDTO()

@Serializable
@SerialName("App")
data class AppDTO(val content: String) : MessageDTO()

@Serializable
@SerialName("Quote")
data class QuoteDTO(
    val id: Int,
    val senderId: Long,
    val targetId: Long,
    val groupId: Long,
    val origin: MessageChainDTO
) : MessageDTO()

@Serializable
@SerialName("Poke")
data class PokeMessageDTO(
    val name: String
) : MessageDTO()

@Serializable
@SerialName("Dice")
data class DiceDTO(
    val value: Int
) : MessageDTO()

@Serializable
@SerialName("MarketFace")
data class MarketFaceDTO(
    val id: Int,
    val name: String,
) : MessageDTO()

@Serializable
@SerialName("MusicShare")
data class MusicShareDTO(
    val kind: String,
    val title: String,
    val summary: String,
    val jumpUrl: String,
    val pictureUrl: String,
    val musicUrl: String,
    val brief: String,
) : MessageDTO()

@Serializable
@SerialName("Forward")
data class ForwardMessageDTO(
    val display: ForwardMessageDisplayDTO? = null,
    val nodeList: List<ForwardMessageNode>,
) : MessageDTO()

@Serializable
data class ForwardMessageDisplayDTO(
    val brief: String? = null,
    val preview: List<String>? = null,
    val source: String? = null,
    val summary: String? = null,
    val title: String? = null,
)

@Serializable
data class ForwardMessageNode(
    val senderId: Long? = null,
    val time: Int? = null,
    val senderName: String? = null,
    val messageChain: MessageChainDTO? = null,
    val messageId: Int? = null,
    val messageRef: MessageIdDTO? = null,
)

@Serializable
@SerialName("File")
data class FileDTO(
    val id: String,
    val name: String,
    val size: Long,
) : MessageDTO()

@Serializable
@SerialName("MiraiCode")
data class MiraiCodeDTO(
    val code: String
) : MessageDTO()

@Serializable
@SerialName("Unknown")
object UnknownMessageDTO : MessageDTO()

@Serializable
sealed class MessageDTO : DTO


// parameter

@Serializable
data class SendDTO(
    val quote: Int? = null,
    val target: Long? = null,
    val qq: Long? = null,
    val group: Long? = null,
    val messageChain: MessageChainDTO
) : DTO

@Serializable
data class SendImageDTO(
    val target: Long? = null,
    val qq: Long? = null,
    val group: Long? = null,
    val urls: List<String>
) : DTO

@Serializable
@Suppress("unused")
class SendRetDTO(
    val code: Int = 0,
    val msg: String = "success",
    val messageId: Int
) : DTO

@Serializable
@Suppress("unused")
class UploadImageRetDTO(
    val imageId: String,
    val url: String,
) : DTO

@Serializable
@Suppress("unused")
class UploadVoiceRetDTO(
    val voiceId: String,
) : DTO

@Serializable
data class MessageIdDTO(
    val target: Long,
    val messageId: Int,
) : DTO

@Serializable
data class RoamingMessageDTO(
    val timeStart: Long,
    val timeEnd: Long,
    val target: Long,
) : DTO