package me.stageguard.aruku.ui.page.home.message

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
import com.valentinilk.shimmer.Shimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
    private val repository: MainRepository
) : ViewModel() {

    private val _messages: MutableState<Flow<PagingData<SimpleMessagePreview>>?> =
        mutableStateOf(null)
    val messages: State<Flow<PagingData<SimpleMessagePreview>>?> get() = _messages

    suspend fun initMessage(account: Long) = withContext(Dispatchers.IO) {
        _messages.value =
            Pager(config = PagingConfig(12/* based on dpi=360, height=2160 */), initialKey = 0) {
                repository.getMessagePreview(account)
            }.flow.map { data ->
                data.map {
                    SimpleMessagePreview(
                        contact = ArukuContact(it.type, it.subject),
                        avatarData = repository.getAvatarUrl(
                            account,
                            ArukuContact(it.type, it.subject)
                        ),
                        name = repository.getNickname(
                            account,
                            ArukuContact(it.type, it.subject)
                        )
                            ?: it.subject.toString(),
                        preview = it.previewContent,
                        time = LocalDateTime.ofEpochSecond(it.time, 0, ZoneOffset.UTC),
                        unreadCount = 1
                    )
                }
            }.cachedIn(viewModelScope)
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
    val unreadCount: Int
)

typealias MessagePreviewOrShimmer = Either<Shimmer, SimpleMessagePreview>