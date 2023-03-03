package me.stageguard.aruku.ui.page.home.message

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valentinilk.shimmer.Shimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.stageguard.aruku.database.LoadState
import me.stageguard.aruku.database.mapOk
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.util.tag
import net.mamoe.mirai.utils.Either

/**
 * Created by LoliBall on 2022/12/31 12:13.
 * https://github.com/WhichWho
 */
class MessageViewModel(
    private val repository: MainRepository,
) : ViewModel() {
    private val currentBotChannel = Channel<Long?>()

    val messages: StateFlow<LoadState<List<SimpleMessagePreview>>> =
        channelFlow {
            val currentBotFlow = currentBotChannel.consumeAsFlow()
            var currentMessageJob: Job? = null

            currentBotFlow.collect { b ->
                currentMessageJob?.cancelAndJoin()

                this@channelFlow.send(LoadState.Loading())

                if (b == null) {
                    this@channelFlow.send(LoadState.Ok(listOf()))
                    return@collect
                }

                currentMessageJob = viewModelScope.launch {
                    repository.getMessagePreview(b).cancellable().mapOk { data ->
                        data.map {
                            SimpleMessagePreview(
                                contact = it.contact,
                                avatarData = repository.getAvatarUrl(b, it.contact),
                                name = repository.getNickname(b, it.contact)
                                    ?: it.contact.subject.toString(),
                                preview = it.previewContent,
                                time = it.time,
                                unreadCount = it.unreadCount,
                                messageId = it.messageId,
                            )
                        }
                    }.collect(this@channelFlow::send)
                }
            }
            Log.w(this@MessageViewModel.tag(), "message channel is completed.")
        }.stateIn(viewModelScope, SharingStarted.Eagerly, LoadState.Loading())

    fun setActiveBot(id: Long?) {
        currentBotChannel.trySend(id)
    }

    fun clearUnreadCount(account: Long, contact: ArukuContact) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearUnreadCount(account, contact)
        }
    }
}

data class SimpleMessagePreview(
    val contact: ArukuContact,
    val avatarData: Any?,
    val name: String,
    val preview: String,
    val time: Long,
    val unreadCount: Int,
    val messageId: Int,
)

typealias MessagePreviewOrShimmer = Either<Shimmer, SimpleMessagePreview>