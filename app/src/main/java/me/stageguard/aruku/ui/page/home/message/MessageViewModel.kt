package me.stageguard.aruku.ui.page.home.message

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.service.parcel.ArukuMessageType
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Created by LoliBall on 2022/12/31 12:13.
 * https://github.com/WhichWho
 */
class MessageViewModel(
    private val arukuServiceInterface: IArukuMiraiInterface,
    private val database: ArukuDatabase,
) : ViewModel() {

    private val _messageSequences = mutableStateListOf<SimpleMessagePreview>()

    val messages get() =  _messageSequences

    // observe message preview changes in message page
    context(CoroutineScope) fun observeMessagePreview(account: Long) {
        val messageFlow = database { messagePreview().getMessages(account) }
        launch {
            messageFlow.collect { msg ->
                val subjects = msg.map { it.type to it.subject }
                _messageSequences.removeIf { it.type to it.subject in subjects }
                _messageSequences.addAll(msg.map {
                    SimpleMessagePreview(
                        type = it.type,
                        subject = it.subject,
                        avatarData = arukuServiceInterface.getAvatar(
                            account,
                            it.type.ordinal,
                            it.subject
                        ),
                        name = arukuServiceInterface.getNickname(
                            account,
                            it.type.ordinal,
                            it.subject
                        )
                            ?: it.subject.toString(),
                        preview = it.previewContent,
                        time = LocalDateTime.ofEpochSecond(it.time, 0, ZoneOffset.UTC),
                        unreadCount = 1
                    )
                })
                _messageSequences.sortByDescending { it.time }
            }
        }
    }


}


data class SimpleMessagePreview(
    val type: ArukuMessageType,
    val subject: Long,
    val avatarData: Any?,
    val name: String,
    val preview: String,
    val time: LocalDateTime,
    val unreadCount: Int
)