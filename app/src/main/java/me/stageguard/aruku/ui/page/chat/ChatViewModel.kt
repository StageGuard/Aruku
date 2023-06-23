package me.stageguard.aruku.ui.page.chat

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.stageguard.aruku.cache.AudioCache
import me.stageguard.aruku.common.createAndroidLogger
import me.stageguard.aruku.common.message.At
import me.stageguard.aruku.common.message.AtAll
import me.stageguard.aruku.common.message.Audio
import me.stageguard.aruku.common.message.Face
import me.stageguard.aruku.common.message.File
import me.stageguard.aruku.common.message.FlashImage
import me.stageguard.aruku.common.message.Forward
import me.stageguard.aruku.common.message.Image
import me.stageguard.aruku.common.message.MessageElement
import me.stageguard.aruku.common.message.PlainText
import me.stageguard.aruku.common.message.Quote
import me.stageguard.aruku.common.service.parcel.ContactType
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.ui.UiState
import me.stageguard.aruku.ui.page.ChatPageNav
import me.stageguard.aruku.util.LoadState
import me.stageguard.aruku.util.toFormattedTime

class ChatViewModel(
    private val repository: MainRepository,
    private val account: Long,
    private val chatNav: ChatPageNav,
) : ViewModel() {
    private val logger = createAndroidLogger()

    private val contact = chatNav.contact

    // key is audio md5
    private val _audioStatus = mutableStateMapOf<String, ChatAudioStatus>()
    // key is message id
    private val _quoteStatus = mutableStateMapOf<Long, ChatQuoteMessageStatus>()
    // key is file message id
    private val _fileStatus = mutableStateMapOf<Long, ChatFileStatus>()

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
    val quote = snapshotFlow { _quoteStatus.toMap() }
    @UiState
    val file = snapshotFlow { _fileStatus.toMap() }

    @UiState
    val messages: Flow<PagingData<ChatElement>> =
        repository.getMessageRecords(account, contact, viewModelScope.coroutineContext)
            .map { data -> data.map { it.mapChatElement() } }
            .cachedIn(viewModelScope)

    private suspend fun MessageRecordEntity.mapChatElement(excludeQuote: Boolean = false): ChatElement {
        val memberInfo by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            if (contact.type != ContactType.FRIEND) repository.getGroupMemberInfo(this@ChatViewModel.account, contact.subject, sender) else null
        }

        val senderNameDeferred = CompletableDeferred<String>()

        fun MutableList<UIMessageElement>.addNonTextElement(content: MessageElement) {
            when (content) {
                is Image -> add(UIMessageElement.Image(
                    content.url, content.uuid, content.width, content.height, content.isEmoticons
                ))
                is FlashImage -> add(UIMessageElement.FlashImage(
                    content.url, content.uuid, content.width, content.height
                ))
                is Audio -> add(UIMessageElement.Audio(content.fileMd5, content.fileName))
                is File -> add(UIMessageElement.File(
                    content.id, content.name, content.extension, content.size, messageId
                ))
                is Forward -> add(UIMessageElement.Unsupported(content.contentToString()))
                is Quote -> if(!excludeQuote) add(UIMessageElement.Quote(content.messageId))
                else -> add(UIMessageElement.Unsupported(content.contentToString()))
            }
        }

        fun MessageElement.mapTextToUITextElement() = when(this) {
            is PlainText -> UIMessageElement.Text.PlainText(text)
            is At -> UIMessageElement.Text.At(target, display)
            is Face -> UIMessageElement.Text.Face(id, name)
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

        viewModelScope.launch {
            repository.updateMessageRecord(copy(senderName = senderNameDeferred.await()))
        }

        return ChatElement.Message(
            senderId = sender,
            senderName = {
                (if (contact.type != ContactType.FRIEND) {
                    memberInfo?.senderName
                } else {
                    repository.getNickname(account, contact)
                } ?: contact.subject.toString()).also { senderNameDeferred.complete(it) }
            },
            senderAvatarUrl = {
                if (contact.type != ContactType.FRIEND) {
                    memberInfo?.senderAvatarUrl
                } else {
                    repository.getAvatarUrl(account, contact)
                }
            },
            time = time.toFormattedTime(),
            messageId = messageId,
            messages = uiMessageElements
        )
    }

    /**
     * start query quote status.
     * This method is called in composable disposable effect scope.
     */
    suspend fun querySingleMessage(messageId: Long) {
        if (_quoteStatus[messageId] != null) return
        _quoteStatus.putIfAbsent(messageId, ChatQuoteMessageStatus.Querying)

        repository.querySingleMessage(account, contact, messageId)
            .cancellable()
            .flowOn(Dispatchers.Main)
            .collect {
                _quoteStatus[messageId] = when(it) {
                    is LoadState.Loading -> ChatQuoteMessageStatus.Querying
                    is LoadState.Ok -> ChatQuoteMessageStatus.Ready(
                        it.value.mapChatElement(true) as ChatElement.Message
                    )
                    is LoadState.Error -> ChatQuoteMessageStatus.Error(it.throwable.message)
                }
            }
    }

    /**
     * start query file status.
     * This method is called in composable disposable effect scope.
     */
    suspend fun queryFileStatus(fileId: String?, fileMessageId: Long) {
        if (_fileStatus[fileMessageId] != null) return
        _fileStatus.putIfAbsent(fileMessageId, ChatFileStatus.Querying)

        repository.queryFileStatus(account, contact, fileId, fileMessageId)
            .cancellable()
            .flowOn(Dispatchers.Main)
            .collect {
                _fileStatus[fileMessageId] = when(it) {
                    is LoadState.Loading -> ChatFileStatus.Querying
                    is LoadState.Ok -> {
                        if (it.value.url == null) ChatFileStatus.Expired
                        else ChatFileStatus.Operational(it.value.url!!)
                    }
                    is LoadState.Error -> ChatFileStatus.Error(it.throwable.message)
                }
         }
    }

    suspend fun queryAudioStatus(audioFileMd5: String) {
        _audioStatus.putIfAbsent(audioFileMd5, ChatAudioStatus.Preparing(0.0))

        repository.queryAudioStatus(audioFileMd5)
            .cancellable()
            .flowOn(Dispatchers.Main)
            .collect {
                val status = when (it) {
                    is AudioCache.State.Error -> ChatAudioStatus.Error(it.msg ?: "unknown error")
                    is AudioCache.State.NotFound -> ChatAudioStatus.NotFound
                    is AudioCache.State.Ready -> ChatAudioStatus.Ready(List(20) { Math.random() })
                    is AudioCache.State.Preparing -> ChatAudioStatus.Preparing(it.progress)
                }
                _audioStatus[audioFileMd5] = status
            }
    }

    private fun MessageElement.isText(): Boolean {
        return this is PlainText || this is At || this is AtAll || this is Face
    }

}

sealed interface ChatElement {
    val uniqueKey: Any

    data class Message(
        val senderId: Long,
        val senderName: suspend () -> String, // lazy load in compose coroutine scope
        val senderAvatarUrl: suspend () -> String?, // lazy load in compose coroutine scope
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
}