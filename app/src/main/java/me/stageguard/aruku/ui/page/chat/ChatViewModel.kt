package me.stageguard.aruku.ui.page.chat

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.stageguard.aruku.cache.AudioCache
import me.stageguard.aruku.database.LoadState
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.domain.data.message.At
import me.stageguard.aruku.domain.data.message.AtAll
import me.stageguard.aruku.domain.data.message.Audio
import me.stageguard.aruku.domain.data.message.Face
import me.stageguard.aruku.domain.data.message.File
import me.stageguard.aruku.domain.data.message.FlashImage
import me.stageguard.aruku.domain.data.message.Forward
import me.stageguard.aruku.domain.data.message.Image
import me.stageguard.aruku.domain.data.message.MessageElement
import me.stageguard.aruku.domain.data.message.PlainText
import me.stageguard.aruku.domain.data.message.Quote
import me.stageguard.aruku.service.bridge.AudioStatusListener
import me.stageguard.aruku.service.parcel.ContactType
import me.stageguard.aruku.ui.UiState
import me.stageguard.aruku.ui.page.ChatPageNav
import me.stageguard.aruku.util.toFormattedTime

class ChatViewModel(
    private val repository: MainRepository,
    private val account: Long,
    private val chatNav: ChatPageNav,
) : ViewModel() {
    private val contact = chatNav.contact

    private val _audioStatus = mutableStateMapOf<String, ChatAudioStatus>()
    private val _queryStatus = mutableStateMapOf<Long, ChatQuoteMessageStatus>()

    @UiState
    val subjectName = flow {
        val name = repository.getNickname(account, contact)
        if (name != null) emit(name)
    }.stateIn(viewModelScope, SharingStarted.Lazily, contact.subject.toString())

    @UiState
    val subjectAvatar = flow<String?> {
        val url = repository.getAvatarUrl(account, contact)
        if (url != null) emit(url)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    @UiState
    val audio = snapshotFlow { _audioStatus.toMap() }
    @UiState
    val quote = snapshotFlow { _queryStatus.toMap() }

    @UiState
    val messages: Flow<PagingData<ChatElement>> =
        repository.getMessageRecords(account, contact).map { data ->
            data.map { it.mapChatElement() }
        }.cachedIn(viewModelScope)

    private suspend fun MessageRecordEntity.mapChatElement(excludeQuote: Boolean = false): ChatElement {
        val memberInfo = if (contact.type == ContactType.GROUP) {
            repository.getGroupMemberInfo(this@ChatViewModel.account, contact.subject, sender)
        } else null

        fun MutableList<UIMessageElement>.addNonTextElement(content: MessageElement) {
            when (content) {
                is Image -> add(UIMessageElement.Image(
                    content.url, content.uuid, content.width, content.height, content.isEmoticons
                ))
                is FlashImage -> add(UIMessageElement.FlashImage(
                    content.url, content.uuid, content.width, content.height
                ))
                is Face -> add(UIMessageElement.Face(content.id))
                is Audio -> add(UIMessageElement.Audio(content.fileMd5, content.fileName))
                is File -> add(UIMessageElement.File(content.name, content.size))
                is Forward -> add(UIMessageElement.Unsupported(content.contentToString()))
                is Quote -> if(!excludeQuote) add(UIMessageElement.Quote(content.messageId))
                else -> add(UIMessageElement.Unsupported(content.contentToString()))
            }
        }

        fun MessageElement.mapTextToUITextElement() = when(this) {
            is PlainText -> UIMessageElement.Text.PlainText(text)
            is At -> UIMessageElement.Text.At(target, display)
            is AtAll -> UIMessageElement.Text.AtAll
            else -> error("unreachable!")
        }

        val annotated = mutableListOf<UIMessageElement.Text>()
        val uiMessageElements = buildList(message.size) {
            message.forEach {
                if (it.isText()) {
                    annotated.add(it.mapTextToUITextElement())
                } else {
                    if (annotated.isNotEmpty()) {
                        add(UIMessageElement.AnnotatedText(annotated.toList()))
                        annotated.clear()
                    }
                    addNonTextElement(it)
                }
            }
            if (annotated.isNotEmpty()) {
                add(UIMessageElement.AnnotatedText(annotated.toList()))
                annotated.clear()
            }
        }

        return ChatElement.Message(
            senderId = sender,
            senderName = senderName,
            senderAvatarUrl = when (contact.type) {
                ContactType.GROUP -> memberInfo?.senderAvatarUrl
                ContactType.FRIEND -> repository.getAvatarUrl(this@ChatViewModel.account, contact)
                ContactType.TEMP -> error("temp message is currently unsupported")
            },
            time = time.toFormattedTime(),
            messageId = messageId,
            messages = uiMessageElements
        )
    }

    fun querySingleMessage(messageId: Long) {
        if (_queryStatus[messageId] != null) return
        _queryStatus[messageId] = ChatQuoteMessageStatus.Querying

        val queryFlow = repository.querySingleMessage(account, contact, messageId)
        viewModelScope.launch {
            queryFlow.collect {
                val status = when(it) {
                    is LoadState.Loading -> ChatQuoteMessageStatus.Querying
                    is LoadState.Ok -> ChatQuoteMessageStatus.Ready(
                        it.data.mapChatElement(true) as ChatElement.Message
                    )
                    is LoadState.Error -> ChatQuoteMessageStatus.Error(it.throwable.message)
                }
                _queryStatus[messageId] = status
            }
        }
    }

    fun attachAudioStatusListener(audioFileMd5: String) {
        repository.attachAudioStatusListener(audioFileMd5, AudioStatusListener {
            val status = when(it) {
                is AudioCache.State.Error -> ChatAudioStatus.Error(it.msg ?: "unknown error")
                is AudioCache.State.NotFound -> ChatAudioStatus.NotFound
                is AudioCache.State.Ready -> ChatAudioStatus.Ready(List(20) { Math.random() })
                is AudioCache.State.Preparing -> ChatAudioStatus.Preparing(it.progress)
            }
            _audioStatus[audioFileMd5] = status
        })
    }

    fun detachAudioStatusListener(audioFileMd5: String) {
        repository.detachAudioStatusListener(audioFileMd5)
    }

}

sealed interface ChatElement {
    val uniqueKey: Any

    data class Message(
        val senderId: Long,
        val senderName: String,
        val senderAvatarUrl: String?,
        val time: String,
        val messageId: Long,
        val messages: List<UIMessageElement>
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

sealed interface ChatQuoteMessageStatus {
    object Querying : ChatQuoteMessageStatus

    class Error(val msg: String?) : ChatQuoteMessageStatus

    class Ready(val msg: ChatElement.Message) : ChatQuoteMessageStatus

}