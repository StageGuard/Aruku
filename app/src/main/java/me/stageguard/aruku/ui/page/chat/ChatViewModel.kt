package me.stageguard.aruku.ui.page.chat

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import me.stageguard.aruku.cache.AudioCache
import me.stageguard.aruku.database.LoadState
import me.stageguard.aruku.database.mapOk
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.ArukuContactType
import me.stageguard.aruku.service.parcel.toArukuAudio
import me.stageguard.aruku.ui.page.ChatPageNav
import me.stageguard.aruku.util.formatHHmm
import net.mamoe.mirai.message.code.MiraiCode.deserializeMiraiCode
import net.mamoe.mirai.message.data.*
import java.time.LocalDateTime
import java.time.ZoneOffset

class ChatViewModel(
    private val repository: MainRepository,
    private val bot: Long,
    private val audioCache: AudioCache,
    private val chatNav: ChatPageNav,
) : ViewModel() {
    val subjectName = flow {
        val name = repository.getNickname(bot, chatNav.contact)
        if (name != null) emit(name)
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        chatNav.contact.subject.toString()
    )
    val subjectAvatar = flow<String?> {
        val url = repository.getAvatarUrl(bot, chatNav.contact)
        if (url != null) emit(url)
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        null
    )

    val messages: StateFlow<LoadState<List<ChatElement>>> =
        repository.getMessageRecords(bot, chatNav.contact.subject, chatNav.contact.type)
            .mapOk { data ->
                data.map { record ->
                    val memberInfo = if (record.type == ArukuContactType.GROUP) {
                        repository.getGroupMemberInfo(
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
                            is Image -> visibleMessages.add(
                                VisibleChatMessage.Image(repository.queryImageUrl(it))
                            )

                            is FlashImage -> visibleMessages.add(
                                VisibleChatMessage.Image(repository.queryImageUrl(it.image))
                            )

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
                                val cache = audioCache.resolveAsFlow(it.toArukuAudio())
                                    .map { result ->
                                        when (result) {
                                            is AudioCache.ResolveResult.NotFound -> ChatAudioStatus.NotFound
                                            is AudioCache.ResolveResult.Ready -> ChatAudioStatus.Ready
                                            is AudioCache.ResolveResult.Preparing -> ChatAudioStatus.Preparing(
                                                result.progress
                                            )
                                        }
                                    }.flowOn(viewModelScope.coroutineContext + Dispatchers.IO)
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
                            ArukuContactType.FRIEND -> repository.getAvatarUrl(
                                bot,
                                ArukuContact(ArukuContactType.FRIEND, record.subject)
                            )

                            ArukuContactType.TEMP -> error("temp message is currently unsupported")
                        } ?: "",
                        time = LocalDateTime.ofEpochSecond(record.time.toLong(), 0, ZoneOffset.UTC)
                            .formatHHmm(),
                        source = "${record.messageIds}${record.messageInternalIds}".toLongOrNull()
                            ?: -1L,
                        visibleMessages = visibleMessages
                    ) as ChatElement
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, LoadState.Loading())

    private val _chatAudios: MutableMap<String, Flow<ChatAudioStatus>> = mutableMapOf()
    val chatAudios: Map<String, Flow<ChatAudioStatus>> = _chatAudios
}

sealed interface VisibleChatMessage {
    data class PlainText(val content: String) : VisibleChatMessage
    data class Image(val url: String?) : VisibleChatMessage
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