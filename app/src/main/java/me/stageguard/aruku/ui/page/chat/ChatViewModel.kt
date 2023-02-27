package me.stageguard.aruku.ui.page.chat

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import me.stageguard.aruku.cache.AudioCache
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.domain.data.message.*
import me.stageguard.aruku.service.parcel.ArukuContactType
import me.stageguard.aruku.service.parcel.toArukuAudio
import me.stageguard.aruku.ui.page.ChatPageNav
import me.stageguard.aruku.util.toFormattedTime
import net.mamoe.mirai.utils.toUHexString

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

    val messages: Flow<PagingData<ChatElement>> =
        repository.getMessageRecords(bot, chatNav.contact.subject, chatNav.contact.type)
            .map { data ->
                data.map { record ->
                    val memberInfo = if (record.contact.type == ArukuContactType.GROUP) {
                        repository.getGroupMemberInfo(
                            bot,
                            record.contact.subject,
                            record.sender
                        )
                    } else null

                    val visibleMessages = buildList(record.message.size) {
                        record.message.forEach {
                            when (it) {
                                is PlainText -> add(VisibleChatMessage.PlainText(it.text))
                                is Image -> add(VisibleChatMessage.Image(it.url))
                                is FlashImage -> add(VisibleChatMessage.Image(it.url))
                                is At -> add(
                                    VisibleChatMessage.At(it.target, it.display)
                                )

                                is AtAll -> add(VisibleChatMessage.AtAll)
                                is Face -> add(VisibleChatMessage.Face(it.id))
                                is Audio -> {
                                    val cache =
                                        audioCache.resolveAsFlow(it.toArukuAudio()).map { result ->
                                            when (result) {
                                                is AudioCache.ResolveResult.NotFound -> ChatAudioStatus.NotFound
                                                is AudioCache.ResolveResult.Ready -> ChatAudioStatus.Ready
                                                is AudioCache.ResolveResult.Preparing -> ChatAudioStatus.Preparing(
                                                    result.progress
                                                )
                                            }
                                        }.flowOn(Dispatchers.IO)

                                    val identity = it.fileMd5.toUHexString()
                                    _chatAudios[identity] = cache

                                    add(VisibleChatMessage.Audio(identity, it.fileName))
                                }

                                is File -> add(VisibleChatMessage.File(it.name, it.size))
                                is Forward -> add(VisibleChatMessage.PlainText(it.contentToString()))
                                else -> add(VisibleChatMessage.Unsupported(it.contentToString()))
                            }
                        }
                    }

                    ChatElement.Message(
                        senderId = record.sender,
                        senderName = record.senderName,
                        senderAvatarUrl = when (record.contact.type) {
                            ArukuContactType.GROUP -> memberInfo?.senderAvatarUrl
                            ArukuContactType.FRIEND -> repository.getAvatarUrl(bot, record.contact)
                            ArukuContactType.TEMP -> error("temp message is currently unsupported")
                        },
                        time = record.time.toLong().toFormattedTime(),
                        messageId = record.messageId,
                        visibleMessages = visibleMessages
                    ) as ChatElement
                }
            }

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
        val nodes: List<me.stageguard.aruku.domain.data.message.Forward.Node>
    ) : VisibleChatMessage

    data class File(val name: String, val size: Long) : VisibleChatMessage

    // PokeMessage, VipFace, LightApp, MarketFace, SimpleServiceMessage, MusicShare, Dice, RockPaperScissors
    data class Unsupported(val content: String) : VisibleChatMessage
}

sealed interface ChatElement {
    data class Message(
        val senderId: Long,
        val senderName: String,
        val senderAvatarUrl: String?,
        val time: String,
        val messageId: Int,
        val visibleMessages: List<VisibleChatMessage>
    ) : ChatElement

    data class Notification(
        val content: String,
        val annotated: List<Pair<IntRange, () -> Unit>>
    ) : ChatElement

    data class DateDivider(val date: String) : ChatElement
}

sealed interface ChatAudioStatus {
    object Ready : ChatAudioStatus
    class Preparing(val progress: Double) : ChatAudioStatus
    object NotFound : ChatAudioStatus
    object Unknown : ChatAudioStatus
}