package me.stageguard.aruku.ui.page.chat

import androidx.annotation.DrawableRes
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.service.parcel.ArukuContact
import net.mamoe.mirai.message.data.ForwardMessage

class ChatViewModel(
    private val arukuServiceInterface: IArukuMiraiInterface,
    private val database: ArukuDatabase,
    private val contact: ArukuContact,
) : ViewModel() {
    private val _subjectName = mutableStateOf(contact.subject.toString())
    val subjectName: State<String> = _subjectName
    private val _subjectAvatar = mutableStateOf<Any?>(null)
    val subjectAvatar: State<Any?> = _subjectAvatar


    context(CoroutineScope) fun initChatInfoState(bot: Long) {
        this@CoroutineScope.launch {
            _subjectName.value = arukuServiceInterface.getNickname(bot, contact)
            _subjectAvatar.value = arukuServiceInterface.getAvatarUrl(bot, contact)
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
    data class Audio(val url: String) : VisibleChatMessage
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
        val source: Int,
        val visibleMessages: List<VisibleChatMessage>
    ) : ChatElement

    data class Notification(val content: String, val annotated: List<Pair<IntRange, () -> Unit>>) : ChatElement
    data class DateDivider(val date: String) : ChatElement
}