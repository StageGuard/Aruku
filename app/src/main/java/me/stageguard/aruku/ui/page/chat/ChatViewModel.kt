package me.stageguard.aruku.ui.page.chat

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.*
import me.stageguard.aruku.cache.AudioCache
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.domain.data.message.*
import me.stageguard.aruku.service.parcel.ArukuContactType
import me.stageguard.aruku.service.parcel.AudioStatusListener
import me.stageguard.aruku.ui.UiState
import me.stageguard.aruku.ui.page.ChatPageNav
import me.stageguard.aruku.util.toFormattedTime

class ChatViewModel(
    private val repository: MainRepository,
    private val bot: Long,
    private val chatNav: ChatPageNav,
) : ViewModel() {
    private val contact = chatNav.contact

    @UiState
    val subjectName = flow {
        val name = repository.getNickname(bot, contact)
        if (name != null) emit(name)
    }.stateIn(viewModelScope, SharingStarted.Lazily, contact.subject.toString())

    @UiState
    val subjectAvatar = flow<String?> {
        val url = repository.getAvatarUrl(bot, contact)
        if (url != null) emit(url)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _audioList = mutableStateMapOf<String, ChatAudioStatus>()
    @UiState
    val audio = snapshotFlow { _audioList.toMap() }

    @UiState
    val messages: Flow<PagingData<ChatElement>> =
        repository.getMessageRecords(bot, contact).map { data ->
            data.map { record ->
                val memberInfo = if (record.contact.type == ArukuContactType.GROUP) {
                    repository.getGroupMemberInfo(bot, record.contact.subject, record.sender)
                } else null

                val visibleMessages = buildList(record.message.size) {
                    record.message.forEach {
                        when (it) {
                            is PlainText -> add(VisibleChatMessage.PlainText(it.text))
                            is Image -> add(VisibleChatMessage.Image(it.url))
                            is FlashImage -> add(VisibleChatMessage.Image(it.url))
                            is At -> add(VisibleChatMessage.At(it.target, it.display))
                            is AtAll -> add(VisibleChatMessage.AtAll)
                            is Face -> add(VisibleChatMessage.Face(it.id))
                            is Audio -> add(VisibleChatMessage.Audio(it.fileMd5, it.fileName))
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
                    time = record.time.toFormattedTime(),
                    messageId = record.messageId,
                    visibleMessages = visibleMessages
                ) as ChatElement
            }
        }.cachedIn(viewModelScope)

    fun attachAudioStatusListener(audioFileMd5: String) {
        repository.attachAudioStatusListener(audioFileMd5, AudioStatusListener {
            val status = when(it) {
                is AudioCache.State.Error -> ChatAudioStatus.Error(it.msg ?: "unknown error")
                is AudioCache.State.NotFound -> ChatAudioStatus.NotFound
                is AudioCache.State.Ready -> ChatAudioStatus.Ready(List(20) { Math.random() })
                is AudioCache.State.Preparing -> ChatAudioStatus.Preparing(it.progress)
            }
            _audioList[audioFileMd5] = status
        })
    }

    fun detachAudioStatusListener(audioFileMd5: String) {
        repository.detachAudioStatusListener(audioFileMd5)
    }

}

@Composable
fun VisibleChatMessage.Audio.disposableObserver(
    onRegister: (identity: String) -> Unit,
    onUnregister: (identity: String) -> Unit
) {
    DisposableEffect(key1 = this) {
        onRegister(identity)
        onDispose { onUnregister(identity) }
    }
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
    val uniqueKey: Any

    data class Message(
        val senderId: Long,
        val senderName: String,
        val senderAvatarUrl: String?,
        val time: String,
        val messageId: Int,
        val visibleMessages: List<VisibleChatMessage>
    ) : ChatElement {
        override val uniqueKey = messageId
    }

    data class Notification(
        val content: String,
        val annotated: List<Pair<IntRange, () -> Unit>>
    ) : ChatElement {
        override val uniqueKey = content + hashCode()
    }

    data class DateDivider(val date: String) : ChatElement {
        override val uniqueKey = date + hashCode()
    }
}

sealed interface ChatAudioStatus {
    class Ready(val waveLine: List<Double>) : ChatAudioStatus
    class Preparing(val progress: Double) : ChatAudioStatus
    object NotFound : ChatAudioStatus
    class Error(val msg: String?) : ChatAudioStatus
}