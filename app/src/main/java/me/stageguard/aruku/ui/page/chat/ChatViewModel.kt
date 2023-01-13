package me.stageguard.aruku.ui.page.chat

import androidx.annotation.DrawableRes
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.stageguard.aruku.cache.AudioCache
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.ArukuContactType
import me.stageguard.aruku.service.parcel.toArukuAudio
import me.stageguard.aruku.util.formatHHmm
import net.mamoe.mirai.message.code.MiraiCode.deserializeMiraiCode
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.AtAll
import net.mamoe.mirai.message.data.Audio
import net.mamoe.mirai.message.data.Face
import net.mamoe.mirai.message.data.FileMessage
import net.mamoe.mirai.message.data.FlashImage
import net.mamoe.mirai.message.data.ForwardMessage
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.PlainText
import java.time.LocalDateTime
import java.time.ZoneOffset

class ChatViewModel(
    private val arukuServiceInterface: IArukuMiraiInterface,
    private val database: ArukuDatabase,
    private val audioCache: AudioCache,
    private val contact: ArukuContact,
) : ViewModel() {
    private val _subjectName = mutableStateOf(contact.subject.toString())
    val subjectName: State<String> = _subjectName
    private val _subjectAvatar = mutableStateOf<Any?>(null)
    val subjectAvatar: State<Any?> = _subjectAvatar
    private val _messages: MutableState<Flow<PagingData<ChatElement>>?> = mutableStateOf(null)
    val messages: State<Flow<PagingData<ChatElement>>?> = _messages

    private val _chatAudios: MutableMap<String, Flow<ChatAudioStatus>> = mutableMapOf()
    val chatAudios: Map<String, Flow<ChatAudioStatus>> = _chatAudios

    context(CoroutineScope) fun init(bot: Long) {
        this@CoroutineScope.launch {
            _subjectName.value = arukuServiceInterface.getNickname(bot, contact)
            _subjectAvatar.value = arukuServiceInterface.getAvatarUrl(bot, contact)

            _messages.value = Pager(config = PagingConfig(10), initialKey = null) {
                database.messageRecords().run {
                    when (contact.type) {
                        ArukuContactType.TEMP -> error("temp message is currently unsupported")
                        else -> getMessagesPaging(bot, contact.subject, contact.type)
                    }
                }
            }.flow.map { data ->
                data.map { record ->
                    val memberInfo = if (record.type == ArukuContactType.GROUP) {
                        arukuServiceInterface.getGroupMemberInfo(
                            bot,
                            record.subject,
                            record.sender
                        )
                    } else null

                    val deserializedMessage = record.message.deserializeMiraiCode()
                    val visibleMessages = mutableListOf<VisibleChatMessage>()
                    deserializedMessage.forEach {
                        when (it) {
                            is PlainText -> visibleMessages.add(VisibleChatMessage.PlainText(it.content))
                            is Image -> visibleMessages.add(VisibleChatMessage.Image(it.queryUrl()))
                            is FlashImage -> visibleMessages.add(VisibleChatMessage.Image(it.image.queryUrl()))
                            is At -> visibleMessages.add(
                                VisibleChatMessage.At(
                                    it.target,
                                    when (record.type) {
                                        ArukuContactType.GROUP -> memberInfo?.senderName
                                        ArukuContactType.FRIEND -> record.senderName
                                        ArukuContactType.TEMP -> error("temp message is currently unsupported")
                                    } ?: it.target.toString()
                                )
                            )

                            is AtAll -> visibleMessages.add(VisibleChatMessage.AtAll)
                            is Face -> visibleMessages.add(VisibleChatMessage.Face(it.id))
                            is Audio -> {
                                val arukuAudio = it.toArukuAudio()
                                val cache = audioCache.resolveAsFlow(arukuAudio)
                                    .map { result ->
                                        when (result) {
                                            is AudioCache.ResolveResult.NotFound -> ChatAudioStatus.NotFound
                                            is AudioCache.ResolveResult.Ready -> ChatAudioStatus.Ready
                                            is AudioCache.ResolveResult.Preparing -> ChatAudioStatus.Preparing(
                                                result.progress
                                            )
                                        }
                                    }.flowOn(viewModelScope.coroutineContext)
                                val identity = it.filename + "_" + it.fileMd5
                                _chatAudios[identity] = cache

                                visibleMessages.add(
                                    VisibleChatMessage.Audio(
                                        it.filename + "_" + it.fileMd5, it.filename
                                    )
                                )
                            }

                            is FileMessage -> visibleMessages.add(
                                VisibleChatMessage.File(
                                    it.name,
                                    it.size
                                )
                            )

                            is ForwardMessage -> visibleMessages.add(VisibleChatMessage.PlainText(it.contentToString()))
                            else -> visibleMessages.add(VisibleChatMessage.Unsupported(it.contentToString()))
                        }
                    }

                    ChatElement.Message(
                        senderId = record.sender,
                        senderName = record.senderName,
                        senderAvatarUrl = when (record.type) {
                            ArukuContactType.GROUP -> memberInfo?.senderAvatarUrl
                            ArukuContactType.FRIEND -> arukuServiceInterface.getAvatarUrl(
                                bot,
                                ArukuContact(ArukuContactType.FRIEND, record.subject)
                            )

                            ArukuContactType.TEMP -> error("temp message is currently unsupported")
                        } ?: "",
                        time = LocalDateTime.ofEpochSecond(record.time.toLong(), 0, ZoneOffset.UTC)
                            .formatHHmm(),
                        source = "${record._prim_key}${record.messageIds}".toLongOrNull() ?: -1L,
                        visibleMessages = visibleMessages
                    ) as ChatElement
                }
            }.cachedIn(viewModelScope)
        }
    }
}

sealed interface VisibleChatMessage {
    data class PlainText(val content: String) : VisibleChatMessage
    data class Image(val url: String) : VisibleChatMessage
    data class At(val targetId: Long, val targetName: String) : VisibleChatMessage
    object AtAll : VisibleChatMessage
    data class Face(val id: Int) : VisibleChatMessage {
        companion object {
            val FACE_MAP = mapOf<Int, @receiver:DrawableRes Int>()
        }
    }

    data class FlashImage(val url: String) : VisibleChatMessage
    data class Audio(val identity: String, val name: String) : VisibleChatMessage
    data class Forward(
        val preview: List<String>,
        val title: String,
        val brief: String,
        val summary: String,
        val nodes: List<ForwardMessage.Node>
    ) : VisibleChatMessage

    data class File(val name: String, val size: Long) : VisibleChatMessage

    // PokeMessage, VipFace, LightApp, MarketFace, SimpleServiceMessage, MusicShare, Dice, RockPaperScissors
    data class Unsupported(val content: String) : VisibleChatMessage
}

sealed interface ChatElement {
    data class Message(
        val senderId: Long,
        val senderName: String,
        val senderAvatarUrl: String,
        val time: String,
        val source: Long,
        val visibleMessages: List<VisibleChatMessage>
    ) : ChatElement

    data class Notification(val content: String, val annotated: List<Pair<IntRange, () -> Unit>>) :
        ChatElement

    data class DateDivider(val date: String) : ChatElement
}

sealed interface ChatAudioStatus {
    object Ready : ChatAudioStatus
    class Preparing(val progress: Double) : ChatAudioStatus
    object NotFound : ChatAudioStatus
    object Unknown : ChatAudioStatus
}