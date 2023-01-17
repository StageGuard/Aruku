package me.stageguard.aruku.ui.page.home.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valentinilk.shimmer.Shimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.stageguard.aruku.database.LoadState
import me.stageguard.aruku.database.mapOk
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.parcel.ArukuContact
import net.mamoe.mirai.utils.Either
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Created by LoliBall on 2022/12/31 12:13.
 * https://github.com/WhichWho
 */
class MessageViewModel(
    private val repository: MainRepository,
    private val bot: Long,
) : ViewModel() {
    private val messageUpdateFlow = MutableStateFlow(0L)
    val messages: StateFlow<LoadState<List<SimpleMessagePreview>>> =
        repository.getMessagePreview(bot).combine(messageUpdateFlow) { data, _ -> data }
            .mapOk { data ->
                data.map {
                    SimpleMessagePreview(
                        contact = ArukuContact(it.type, it.subject),
                        avatarData = repository.getAvatarUrl(
                            bot,
                            ArukuContact(it.type, it.subject)
                        ),
                        name = repository.getNickname(
                            bot,
                            ArukuContact(it.type, it.subject)
                        ) ?: it.subject.toString(),
                        preview = it.previewContent,
                        time = LocalDateTime.ofEpochSecond(it.time, 0, ZoneOffset.UTC),
                        unreadCount = it.unreadCount,
                        messageId = it.messageId,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, LoadState.Loading())

    fun clearUnreadCount(account: Long, contact: ArukuContact) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearUnreadCount(account, contact)
        }
    }

    suspend fun updateMessages() {
        messageUpdateFlow.emit(System.currentTimeMillis())
    }
//
////        delay(3000)
////        val mock = buildList {
////            for (i in 1..100) {
////                this += R.mipmap.ic_launcher to "MockUserName$i"
////            }
////        }.shuffled().map { (icon, message) ->
////            SimpleMessagePreview(
////                ArukuMessageType.GROUP,
////                System.nanoTime(),
////                icon,
////                message,
////                "message preview",
////                LocalDateTime.now().minusMinutes((0L..3600L).random()),
////                (0..100).random()
////            )
////        }
////        messages.value = flow { PagingData.from(mock) }
//    }

}

data class SimpleMessagePreview(
    val contact: ArukuContact,
    val avatarData: Any?,
    val name: String,
    val preview: String,
    val time: LocalDateTime,
    val unreadCount: Int,
    val messageId: Long,
)

typealias MessagePreviewOrShimmer = Either<Shimmer, SimpleMessagePreview>